#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
双色球模型回测模块（实现项 7）
================================
采用 walk-forward（滚动训练 / 验证）方式评估模型在历史各期的命中表现。

回测策略：
  1. 在期号 i 处，用 history[:i] 训练轻量模型（RF/XGB 多目标 + Markov），
     预测第 i 期真实开奖的多目标典型概率。
  2. 判定各目标是否"命中"：模型预测该期典型概率 >= threshold，
     且该期真实标签为 1（真实统计量确实落在典型区间）。
  3. 同时统计"候选生成命中率"：auto_predict 流程生成的 Top-K 候选里，
     是否包含当期真实开奖的红球组合（或部分命中）。
  4. 输出每个目标的 Precision / Recall / F1，以及候选命中统计。

注：为控制回测耗时，深度学习模型默认不参与每期重训（--with-dl 开启）。
用法：
  python backtest.py --history path/to/history.csv --start 0 --end 50 --step 5
"""

import os
import sys
import json
import argparse
import random
import numpy as np
from datetime import datetime

if sys.platform == 'win32':
    try:
        import io
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
        sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
    except Exception:
        pass

import ssq_features as F

DEFAULT_THRESHOLD = 0.5
NEG_RATIO = 5
RANDOM_SEED = 42


def log(msg):
    print(msg, flush=True)


def load_history(path):
    import pandas as pd
    df = pd.read_csv(path).sort_values('issue').reset_index(drop=True)
    records = []
    for _, row in df.iterrows():
        reds = [int(row[f'red{i}']) for i in range(1, 7)]
        blue = int(row['blue'])
        records.append((reds, blue))
    return records


def build_rows(history):
    """与 train_models.build_dataset 一致的轻量版（不构造序列上下文用于 DL）"""
    rng = random.Random(RANDOM_SEED)
    rows = []
    for i in range(1, len(history)):
        reds, blue = history[i]
        prev_reds, prev_blue = history[i - 1]
        red_missing = F.compute_red_missing([history[j][0] for j in range(i)])
        blue_missing = F.compute_blue_missing([history[j][1] for j in range(i)])
        feats = F.compute_features(reds, blue, prev_reds, prev_blue,
                                   red_missing, blue_missing)
        labels = F.compute_labels(reds, blue)
        row = {**feats, **labels}
        row['is_positive'] = 1
        row['issue_idx'] = i
        row['state_id'] = F.markov_state_id(reds)
        row['_reds'] = reds
        row['_blue'] = blue
        rows.append(row)
    # 负样本
    n_neg = len(rows) * NEG_RATIO
    for _ in range(n_neg):
        idx = rng.randint(1, len(history) - 1)
        prev_reds, prev_blue = history[idx - 1]
        red_missing = F.compute_red_missing([history[j][0] for j in range(idx)])
        blue_missing = F.compute_blue_missing([history[j][1] for j in range(idx)])
        reds, blue = F.random_combination(rng)
        feats = F.compute_features(reds, blue, prev_reds, prev_blue,
                                   red_missing, blue_missing)
        labels = F.compute_labels(reds, blue)
        row = {**feats, **labels}
        row['is_positive'] = 0
        row['issue_idx'] = idx
        row['state_id'] = F.markov_state_id(reds)
        row['_reds'] = reds
        row['_blue'] = blue
        rows.append(row)
    return rows


def train_light_models(train_rows):
    """训练轻量多目标 RF + XGB + Markov（回测用，不训 DL）"""
    from sklearn.ensemble import RandomForestClassifier
    import xgboost as xgb

    X = np.array([[r[c] for c in F.FEATURE_COLUMNS] for r in train_rows], dtype=float)
    models = {}
    for label in F.LABEL_COLUMNS:
        y = np.array([r[label] for r in train_rows], dtype=int)
        if y.sum() < 5:
            continue
        rf = RandomForestClassifier(n_estimators=60, max_depth=8,
                                    random_state=RANDOM_SEED, n_jobs=-1)
        rf.fit(X, y)
        models[f'rf_{label}'] = rf
        xgb_clf = xgb.XGBClassifier(n_estimators=60, max_depth=5, learning_rate=0.1,
                                    random_state=RANDOM_SEED, eval_metric='logloss',
                                    use_label_encoder=False)
        xgb_clf.fit(X, y)
        models[f'xgb_{label}'] = xgb_clf

    # Markov
    states = sorted({r['state_id'] for r in train_rows})
    state_to_idx = {s: i for i, s in enumerate(states)}
    trans = np.zeros((len(states), 2))
    counts = np.zeros(len(states))
    for r in train_rows:
        idx = state_to_idx[r['state_id']]
        trans[idx, r[F.PRIMARY_LABEL]] += 1
        counts[idx] += 1
    for i in range(len(states)):
        trans[i] = (trans[i] + 1) / (counts[i] + 2) if counts[i] > 0 else [0.5, 0.5]
    models['markov'] = {'transition_matrix': trans, 'state_to_idx': state_to_idx}
    return models


def predict_light(models, reds, blue, history, idx):
    """对第 idx 期真实开奖预测各目标概率"""
    prev_reds, prev_blue = history[idx - 1]
    red_missing = F.compute_red_missing([history[j][0] for j in range(idx)])
    blue_missing = F.compute_blue_missing([history[j][1] for j in range(idx)])
    feats = F.compute_features(reds, blue, prev_reds, prev_blue,
                               red_missing, blue_missing)
    feat_vec = np.array([[feats[c] for c in F.FEATURE_COLUMNS]], dtype=float)
    probs = {}
    for label in F.LABEL_COLUMNS:
        for prefix in ('rf', 'xgb'):
            key = f'{prefix}_{label}'
            if key in models:
                probs[label] = float(models[key].predict_proba(feat_vec)[0][1])
                break
    return probs, feats


def evaluate(history, start, end, step, threshold, top_k, n_candidates):
    """
    walk-forward 回测。
    start/end：相对最新一期的偏移（0=最新一期，向前推）。
    step：每隔 step 期回测一次（减少耗时）。
    """
    n = len(history)
    # 回测期索引（真实开奖的 history 索引）
    eval_indices = list(range(max(n - end, 50), n - start, step))
    log(f"[回测] 共 {len(eval_indices)} 个回测点，threshold={threshold}, top_k={top_k}")

    # 每目标：TP/FP/FN
    stats = {label: {'tp': 0, 'fp': 0, 'fn': 0, 'tn': 0} for label in F.LABEL_COLUMNS}
    # 候选生成命中统计
    candidate_stats = {'red_full_hit': 0, 'red5_hit': 0, 'red4_hit': 0,
                       'blue_hit': 0, 'total': 0}

    for idx in eval_indices:
        # 用 history[:idx] 训练（不含第 idx 期）
        train_history = history[:idx]
        train_rows = build_rows(train_history)
        models = train_light_models(train_rows)

        # 对第 idx 期真实开奖打分
        actual_reds, actual_blue = history[idx]
        probs, feats = predict_light(models, actual_reds, actual_blue, history, idx)
        labels = F.compute_labels(actual_reds, actual_blue)

        for label in F.LABEL_COLUMNS:
            p = probs.get(label)
            if p is None:
                continue
            pred = 1 if p >= threshold else 0
            actual = labels[label]
            s = stats[label]
            if pred == 1 and actual == 1:
                s['tp'] += 1
            elif pred == 1 and actual == 0:
                s['fp'] += 1
            elif pred == 0 and actual == 1:
                s['fn'] += 1
            else:
                s['tn'] += 1

        # 候选生成命中率：用同一批模型对智能采样候选打分，看 Top-K 是否覆盖真实开奖
        rng = random.Random(RANDOM_SEED + idx)
        cand = F.smart_sample_combinations(
            [h[0] for h in train_history], [h[1] for h in train_history],
            n_candidates, rng, hot_weight=0.6)
        actual_red_set = set(actual_reds)
        scored = []
        for reds, blue in cand:
            prev_reds, prev_blue = history[idx - 1]
            red_missing = F.compute_red_missing([history[j][0] for j in range(idx)])
            blue_missing = F.compute_blue_missing([history[j][1] for j in range(idx)])
            f = F.compute_features(reds, blue, prev_reds, prev_blue,
                                   red_missing, blue_missing)
            fv = np.array([[f[c] for c in F.FEATURE_COLUMNS]], dtype=float)
            # 主目标概率作为分数
            score = 0.0
            primary_rf = f'rf_{F.PRIMARY_LABEL}'
            if primary_rf not in models and 'rf_label_sum' in models:
                primary_rf = 'rf_label_sum'
            if primary_rf in models:
                score = float(models[primary_rf].predict_proba(fv)[0][1])
            scored.append((reds, blue, score))
        scored.sort(key=lambda x: x[2], reverse=True)
        top = scored[:top_k]
        candidate_stats['total'] += 1
        if any(set(r) == actual_red_set for r, _, _ in top):
            candidate_stats['red_full_hit'] += 1
        if any(len(actual_red_set & set(r)) >= 5 for r, _, _ in top):
            candidate_stats['red5_hit'] += 1
        if any(len(actual_red_set & set(r)) >= 4 for r, _, _ in top):
            candidate_stats['red4_hit'] += 1
        if any(b == actual_blue for _, b, _ in top):
            candidate_stats['blue_hit'] += 1

        log(f"  期 idx={idx}  真实红球={actual_reds} 蓝球={actual_blue}  "
            f"sum_prob={probs.get('label_sum', 0):.3f}  "
            f"Top-{top_k} 红球全中={'是' if any(set(r)==actual_red_set for r,_,_ in top) else '否'}")

    # 汇总
    log("\n" + "=" * 70)
    log(" 回测汇总")
    log("=" * 70)
    log(f"{'目标':<18}{'Precision':>12}{'Recall':>10}{'F1':>10}{'支持数':>10}")
    results = {}
    for label in F.LABEL_COLUMNS:
        s = stats[label]
        tp, fp, fn = s['tp'], s['fp'], s['fn']
        prec = tp / (tp + fp) if (tp + fp) > 0 else 0.0
        rec = tp / (tp + fn) if (tp + fn) > 0 else 0.0
        f1 = 2 * prec * rec / (prec + rec) if (prec + rec) > 0 else 0.0
        support = tp + fn
        log(f"{label:<18}{prec:>12.4f}{rec:>10.4f}{f1:>10.4f}{support:>10}")
        results[label] = {'precision': prec, 'recall': rec, 'f1': f1,
                          'tp': tp, 'fp': fp, 'fn': fn, 'support': support}

    log("\n[候选生成命中率]")
    total = candidate_stats['total']
    for k in ('red_full_hit', 'red5_hit', 'red4_hit', 'blue_hit'):
        v = candidate_stats[k]
        log(f"  {k:<16}: {v}/{total} = {v / max(total, 1):.4f}")

    return {'targets': results, 'candidate_stats': candidate_stats,
            'config': {'start': start, 'end': end, 'step': step,
                       'threshold': threshold, 'top_k': top_k,
                       'n_candidates': n_candidates,
                       'eval_points': len(eval_indices),
                       'timestamp': datetime.now().isoformat()}}


def parse_arguments():
    parser = argparse.ArgumentParser(description='双色球模型回测（walk-forward）')
    parser.add_argument('--history', required=True, help='history.csv 路径')
    parser.add_argument('--start', type=int, default=0, help='回测起点（相对最新的偏移）')
    parser.add_argument('--end', type=int, default=50, help='回测终点（相对最新的偏移）')
    parser.add_argument('--step', type=int, default=5, help='每隔 step 期回测一次')
    parser.add_argument('--threshold', type=float, default=DEFAULT_THRESHOLD, help='典型概率阈值')
    parser.add_argument('--top-k', type=int, default=20, help='候选 Top-K')
    parser.add_argument('--n-candidates', type=int, default=2000, help='每期候选生成数')
    parser.add_argument('--output', default=None, help='结果 JSON 输出路径')
    return parser.parse_args()


def main():
    args = parse_arguments()
    if not os.path.exists(args.history):
        log(f"[错误] history.csv 不存在: {args.history}")
        sys.exit(1)
    log(f"\n[回测] {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    log(f"[回测] history={args.history}")
    history = load_history(args.history)
    log(f"[回测] 历史 {len(history)} 期")
    result = evaluate(history, args.start, args.end, args.step,
                      args.threshold, args.top_k, args.n_candidates)
    if args.output:
        with open(args.output, 'w', encoding='utf-8') as f:
            json.dump(result, f, ensure_ascii=False, indent=2)
        log(f"\n[回测] 结果已保存: {args.output}")


if __name__ == "__main__":
    main()
