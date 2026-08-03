#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
双色球预测服务（长连接模式，Java 端通过 stdin/stdout JSON 调用）
============================================================
优化点：
  1. Markov 模型预测时直接用候选号码的 state_id 查表，不再借用 ml_features 字段
  2. 启动时加载 history.csv，特征全部内部计算，Java 只需传 red_balls + blue_ball
  3. LSTM / Transformer 真正参与 second_fusion_model 融合
  4. 多目标模型支持：second_fusion 融合主目标，aux 模型用于候选多准则打分
  5. auto_predict 实现候选生成 + 模型打分，返回 Top-K 推荐组合
  6. 兼容旧协议：predict 响应仍为 {"probability":..., "reason":...}

请求类型：
  - init_check    : 初始化检查
  - predict       : 单组号码预测（传 red_balls/blue_ball 或 features）
  - auto_predict  : 候选生成 + 打分，返回 Top-K
"""

import os
import sys
import json
import pickle
import warnings
import argparse
import random
import numpy as np

warnings.filterwarnings('ignore')
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'

import ssq_features as F


def log(msg):
    print(msg, file=sys.stderr, flush=True)


# ============================== 参数解析 ==============================
def parse_arguments():
    parser = argparse.ArgumentParser(description='双色球预测服务（优化版）')
    parser.add_argument('--model-dir', type=str, default=None,
                        help='模型文件目录路径')
    parser.add_argument('--data-dir', type=str, default=None,
                        help='CSV 数据目录路径（含 history.csv），默认为 model-dir 的父目录')
    return parser.parse_args()


args = parse_arguments()
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
if args.model_dir:
    MODEL_DIR = os.path.abspath(args.model_dir)
else:
    MODEL_DIR = os.path.join(BASE_DIR, '..', 'models')
if args.data_dir:
    DATA_DIR = os.path.abspath(args.data_dir)
else:
    DATA_DIR = os.path.dirname(MODEL_DIR.rstrip(os.sep)) or MODEL_DIR

log(f"[启动] 双色球预测服务 - 优化版")
log(f"[模型目录] {MODEL_DIR}")
log(f"[数据目录] {DATA_DIR}")

if not os.path.exists(MODEL_DIR):
    log(f"[错误] 模型目录不存在: {MODEL_DIR}")
    sys.exit(1)


# ============================== 历史数据加载 ==============================
def load_history():
    """加载 history.csv，返回按期号升序的 (reds, blue) 列表"""
    path = os.path.join(DATA_DIR, 'history.csv')
    if not os.path.exists(path):
        log(f"[警告] history.csv 不存在: {path}，动态特征将退化为 0")
        return []
    import csv
    records = []
    with open(path, newline='', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            try:
                reds = [int(row[f'red{i}']) for i in range(1, 7)]
                blue = int(row['blue'])
                records.append((reds, blue))
            except Exception:
                continue
    log(f"[历史] 加载 {len(records)} 期开奖记录")
    return records


HISTORY = load_history()
# 预计算当前历史状态下的遗漏字典（预测时使用）
LAST_REDS, LAST_BLUE = (HISTORY[-1] if HISTORY else ([], 0))
HISTORY_REDS = [r[0] for r in HISTORY]
HISTORY_BLUES = [r[1] for r in HISTORY]
RED_MISSING = F.compute_red_missing(HISTORY_REDS)
BLUE_MISSING = F.compute_blue_missing(HISTORY_BLUES)
# 最近 LOOKBACK 期，顺序与训练一致：t1=上期 ... t5=上5期（newest -> oldest）
# 训练侧 seq_ctx = [history[i-t] for t in range(1, LOOKBACK+1)] 即 newest->oldest
_last_n = HISTORY[-F.LOOKBACK:] if len(HISTORY) >= F.LOOKBACK else HISTORY[:]
SEQ_CTX = list(reversed(_last_n))


# ============================== 模型加载 ==============================
loaded_models = {}


def _load_pickle(name):
    path = os.path.join(MODEL_DIR, name)
    if not os.path.exists(path):
        return None
    with open(path, 'rb') as f:
        return pickle.load(f)


def load_traditional_ml_models():
    """加载多目标 RF / XGB 模型"""
    log("[加载] 多目标 RF / XGB 模型...")
    for label in F.LABEL_COLUMNS:
        for prefix in ('rf', 'xgb'):
            key = f'{prefix}_{label}'
            data = _load_pickle(f'{key}.pkl')
            if data:
                loaded_models[key] = data['model']
                loaded_models[f'{key}_features'] = data.get('feature_columns', F.FEATURE_COLUMNS)
    n = sum(1 for k in loaded_models if k.endswith('_model') or
            (k.startswith(('rf_', 'xgb_')) and not k.endswith('_features')))
    log(f"  ✓ 多目标 ML 模型已加载")


def load_fusion_models():
    """加载一层融合 + 二层融合"""
    log("[加载] 融合模型...")
    for name in ('voting_model', 'stacking_model'):
        data = _load_pickle(f'{name}.pkl')
        if data:
            loaded_models[name] = data['model']
            loaded_models[f'{name}_features'] = data.get('feature_columns', F.FEATURE_COLUMNS)
            log(f"  ✓ {name}.pkl")
    data = _load_pickle('second_fusion_model.pkl')
    if data:
        loaded_models['second_fusion_model'] = data['model']
        loaded_models['second_fusion_meta_features'] = data.get('meta_feature_names', [])
        loaded_models['second_fusion_weights'] = data.get('weights', [])
        log(f"  ✓ second_fusion_model.pkl  元模型={data.get('meta_feature_names', [])}")


def load_lstm_model():
    log("[加载] LSTM 模型...")
    try:
        import tensorflow as tf  # noqa
        from tensorflow import keras
        model_path = os.path.join(MODEL_DIR, 'lstm_model.keras')
        if not os.path.exists(model_path):
            model_path = os.path.join(MODEL_DIR, 'lstm_model.h5')
        scaler_data = _load_pickle('lstm_scaler.pkl')
        if os.path.exists(model_path) and scaler_data:
            model = keras.models.load_model(model_path)
            loaded_models['lstm_model'] = model
            loaded_models['lstm_scaler'] = scaler_data['scaler']
            loaded_models['lstm_n_features'] = scaler_data['n_features']
            loaded_models['lstm_features'] = scaler_data.get('feature_columns', F.sequence_columns())
            log(f"  ✓ LSTM 已加载")
        else:
            log("  ✗ LSTM 模型文件不存在")
    except Exception as e:
        log(f"  ✗ LSTM 加载失败: {e}")


def load_transformer_model():
    log("[加载] Transformer 模型...")
    try:
        import torch
        import torch.nn as nn

        class TransformerClassifier(nn.Module):
            def __init__(self, input_dim, d_model=64, nhead=4, num_layers=2, dropout=0.1):
                super().__init__()
                self.input_projection = nn.Linear(input_dim, d_model)
                encoder_layer = nn.TransformerEncoderLayer(
                    d_model=d_model, nhead=nhead, dim_feedforward=128,
                    dropout=dropout, batch_first=True)
                self.transformer_encoder = nn.TransformerEncoder(encoder_layer, num_layers)
                self.fc1 = nn.Linear(d_model, 32)
                self.dropout = nn.Dropout(dropout)
                self.fc2 = nn.Linear(32, 1)
                self.sigmoid = nn.Sigmoid()

            def forward(self, x):
                x = x.unsqueeze(1)
                x = self.input_projection(x)
                x = self.transformer_encoder(x)
                x = x.squeeze(1)
                x = torch.relu(self.fc1(x))
                x = self.dropout(x)
                x = self.fc2(x)
                return self.sigmoid(x)

        ckpt_path = os.path.join(MODEL_DIR, 'transformer_model.pt')
        scaler_data = _load_pickle('transformer_scaler.pkl')
        if os.path.exists(ckpt_path) and scaler_data:
            ckpt = torch.load(ckpt_path, map_location='cpu')
            device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
            model = TransformerClassifier(ckpt['input_dim']).to(device)
            model.load_state_dict(ckpt['model_state_dict'])
            model.eval()
            loaded_models['transformer_model'] = model
            loaded_models['transformer_scaler'] = scaler_data['scaler']
            loaded_models['transformer_device'] = device
            loaded_models['transformer_features'] = ckpt.get('feature_columns', F.sequence_columns())
            log("  ✓ Transformer 已加载")
        else:
            log("  ✗ Transformer 模型文件不存在")
    except Exception as e:
        log(f"  ✗ Transformer 加载失败: {e}")


def load_markov_model():
    log("[加载] Markov 模型...")
    data = _load_pickle('markov_model.pkl')
    if data:
        loaded_models['markov_model'] = data
        log(f"  ✓ markov_model.pkl ({data.get('n_states', 0)} 个状态)")
    else:
        log("  ✗ markov_model.pkl 不存在")


def load_all_models():
    log("\n" + "=" * 60)
    log("开始加载所有模型...")
    log("=" * 60)
    load_traditional_ml_models()
    load_lstm_model()
    load_transformer_model()
    load_markov_model()
    load_fusion_models()
    model_keys = [k for k in loaded_models if k.endswith('_model')]
    log(f"\n[就绪] 已加载 {len(model_keys)} 个模型: {model_keys}")


# ============================== 特征构造（预测时） ==============================
def build_features_for_prediction(red_balls, blue_ball):
    """
    用当前全量历史状态构造单组号码的特征向量 + 序列向量 + state_id。
    Java 端只需传号码，特征全部内部计算。
    """
    feats = F.compute_features(red_balls, blue_ball, LAST_REDS, LAST_BLUE,
                               RED_MISSING, BLUE_MISSING)
    feat_vec = np.array([[feats[c] for c in F.FEATURE_COLUMNS]], dtype=float)
    seq_vec = np.array([F.sequence_vector(red_balls, blue_ball, SEQ_CTX)], dtype=float)
    state_id = F.markov_state_id(red_balls)
    return feats, feat_vec, seq_vec, state_id


def model_proba(model_key, feat_vec, seq_vec, state_id):
    """获取单个模型对主目标的正类概率"""
    if model_key not in loaded_models:
        return None
    if model_key in ('rf_label_sum', 'xgb_label_sum', 'voting_model', 'stacking_model'):
        return float(loaded_models[model_key].predict_proba(feat_vec)[0][1])
    if model_key == 'lstm_model':
        try:
            scaler = loaded_models['lstm_scaler']
            n = loaded_models['lstm_n_features']
            x = scaler.transform(seq_vec).reshape((-1, n, 1))
            return float(loaded_models['lstm_model'].predict(x, verbose=0).flatten()[0])
        except Exception as e:
            log(f"[lstm 预测失败] {e}")
            return None
    if model_key == 'transformer_model':
        try:
            import torch
            scaler = loaded_models['transformer_scaler']
            device = loaded_models['transformer_device']
            x = torch.FloatTensor(scaler.transform(seq_vec)).to(device)
            with torch.no_grad():
                return float(loaded_models['transformer_model'](x).cpu().numpy().flatten()[0])
        except Exception as e:
            log(f"[transformer 预测失败] {e}")
            return None
    if model_key == 'markov_model':
        data = loaded_models['markov_model']
        idx = data['state_to_idx'].get(state_id)
        if idx is None:
            return 0.5
        return float(data['transition_matrix'][idx, 1])
    return None


def fuse_prediction(feat_vec, seq_vec, state_id):
    """第二层融合：收集元模型概率并喂入元学习器"""
    meta_names = loaded_models.get('second_fusion_meta_features', [])
    name_to_key = {
        'rf': 'rf_label_sum',
        'xgb': 'xgb_label_sum',
        'voting': 'voting_model',
        'stacking': 'stacking_model',
        'lstm': 'lstm_model',
        'transformer': 'transformer_model',
        'markov': 'markov_model',
    }
    meta_probas = []
    used = []
    for name in meta_names:
        key = name_to_key.get(name, name)
        p = model_proba(key, feat_vec, seq_vec, state_id)
        if p is not None:
            meta_probas.append(p)
            used.append(name)
    if len(meta_probas) < 2:
        # 降级：直接用 xgb 或 rf 主目标
        for fallback in ('xgb_label_sum', 'rf_label_sum'):
            p = model_proba(fallback, feat_vec, seq_vec, state_id)
            if p is not None:
                return p, f"Fallback_{fallback}_prob={p:.4f}"
        return 0.5, "No_model_available"
    X_meta = np.array([meta_probas])
    prob = float(loaded_models['second_fusion_model'].predict_proba(X_meta)[0][1])
    weights = loaded_models.get('second_fusion_weights', [])
    reason = f"Second_Layer_Fusion(Ensemble_{len(meta_probas)})_prob={prob:.4f}"
    if weights and len(weights) == len(meta_names):
        contrib = sorted(zip(meta_names, weights, meta_probas),
                         key=lambda x: abs(x[1]), reverse=True)[:3]
        reason += " | Main: " + ", ".join(
            f"{n}(w={w:.3f},p={p:.3f})" for n, w, p in contrib)
    return prob, reason


# ============================== 请求处理 ==============================
def handle_init_check(_request):
    model_keys = [k for k in loaded_models if k.endswith('_model')]
    return {
        'status': 'initialized',
        'models_loaded': len(model_keys),
        'model_list': model_keys,
        'history_loaded': len(HISTORY),
        'message': 'Python 预测服务初始化完成（优化版）'
    }


def handle_prediction(request):
    """
    单组号码预测。
    优先使用 red_balls + blue_ball（新协议，内部计算特征）；
    兼容旧协议 features 字段（仅当未传号码时）。
    响应格式保持 {"probability":..., "reason":...}，供 Java 端解析存库。
    """
    try:
        red_balls = request.get('red_balls')
        blue_ball = request.get('blue_ball')
        model_name = request.get('model', 'second_fusion_model')

        if red_balls is not None and blue_ball is not None:
            red_balls = [int(x) for x in red_balls]
            blue_ball = int(blue_ball)
            feats, feat_vec, seq_vec, state_id = build_features_for_prediction(red_balls, blue_ball)
        else:
            # 旧协议：仅传 features 字典，序列/Markov 退化为占位
            feats_dict = request.get('features', {})
            feat_vec = np.array([[float(feats_dict.get(c, 0)) for c in F.FEATURE_COLUMNS]],
                                dtype=float)
            seq_vec = np.zeros((1, 7 * (F.LOOKBACK + 1)))
            state_id = F.markov_state_id(
                [int(feats_dict.get(f'zone{i}_count', 1)) for i in (1, 2, 3)]) \
                if False else 222  # 旧协议无法精确还原 state_id

        if model_name == 'second_fusion_model':
            if 'second_fusion_model' not in loaded_models:
                # 降级到 xgb 主目标
                for fb in ('xgb_label_sum', 'rf_label_sum'):
                    if fb in loaded_models:
                        p = model_proba(fb, feat_vec, seq_vec, state_id)
                        return {'probability': p, 'reason': f'Fallback_{fb}_prob={p:.4f}'}
                return {'error': 'second_fusion_model 未加载且无可用降级模型'}
            prob, reason = fuse_prediction(feat_vec, seq_vec, state_id)
            return {'probability': prob, 'reason': reason}

        # 指定单模型预测
        key_map = {
            'rf_model': 'rf_label_sum',
            'xgb_model': 'xgb_label_sum',
            'lstm_model': 'lstm_model',
            'transformer_model': 'transformer_model',
            'markov_model': 'markov_model',
            'voting_model': 'voting_model',
            'stacking_model': 'stacking_model',
        }
        key = key_map.get(model_name, model_name)
        p = model_proba(key, feat_vec, seq_vec, state_id)
        if p is None:
            return {'error': f'模型 {model_name} 未加载或预测失败'}
        return {'probability': p,
                'reason': f'{model_name}_prob={p:.4f} | '
                          f'sum={feats.get("sum_red")},span={feats.get("span_red")},'
                          f'odd={feats.get("odd_count")},ac={feats.get("ac_value")}'}
    except Exception as e:
        import traceback
        return {'error': f'预测失败: {e}', 'traceback': traceback.format_exc()}


def handle_auto_predict(request):
    """
    候选生成 + 模型打分（实现项 5）。
    参数：
      n_candidates : 候选生成数量（默认 2000）
      top_k        : 返回 Top-K（默认 20）
      hot_weight   : 智能采样热号权重（默认 0.6）
    返回：{status, recommendations:[{red_balls, blue_ball, score, details}...]}
    """
    try:
        log("[自动预测] 开始生成推荐号码...")
        n_candidates = int(request.get('n_candidates', 2000))
        top_k = int(request.get('top_k', 20))
        hot_weight = float(request.get('hot_weight', 0.6))
        seed = request.get('seed')
        rng = random.Random(seed) if seed is not None else random.Random()

        if 'second_fusion_model' not in loaded_models:
            return {'status': 'error',
                    'error': 'second_fusion_model 未加载，无法执行自动预测'}

        # 1) 候选生成（智能采样）
        candidates = F.smart_sample_combinations(
            HISTORY_REDS, HISTORY_BLUES, n_candidates, rng, hot_weight)
        log(f"  生成 {len(candidates)} 组候选")

        # 2) 逐组打分
        scored = []
        for reds, blue in candidates:
            feats, feat_vec, seq_vec, state_id = build_features_for_prediction(reds, blue)
            prob, _ = fuse_prediction(feat_vec, seq_vec, state_id)

            # 多准则辅助打分：各目标典型概率
            aux = {}
            for label in F.LABEL_COLUMNS:
                for prefix in ('rf', 'xgb'):
                    key = f'{prefix}_{label}'
                    if key in loaded_models:
                        try:
                            aux[f'{prefix}_{label}'] = float(
                                loaded_models[key].predict_proba(feat_vec)[0][1])
                        except Exception:
                            pass
                        break

            # 综合分 = 融合主概率 * 多目标典型性几何平均
            typical_probs = [aux.get(f'rf_{l}', 1.0) for l in F.LABEL_COLUMNS
                             if f'rf_{l}' in loaded_models or f'xgb_{l}' in loaded_models]
            typical_geo = 1.0
            for p in typical_probs:
                typical_geo *= max(p, 1e-6)
            typical_geo = typical_geo ** (1.0 / max(len(typical_probs), 1))
            score = prob * typical_geo

            scored.append({
                'red_balls': reds,
                'blue_ball': blue,
                'score': score,
                'fusion_prob': prob,
                'aux': aux,
                'features': {k: feats[k] for k in
                             ('sum_red', 'span_red', 'odd_count', 'ac_value',
                              'max_missing', 'zone1_count', 'zone2_count', 'zone3_count')}
            })

        # 3) 排序取 Top-K
        scored.sort(key=lambda x: x['score'], reverse=True)
        top = scored[:top_k]

        # 4) 构造推荐理由
        recommendations = []
        for item in top:
            reason = (f"score={item['score']:.4f} fusion={item['fusion_prob']:.4f} "
                      f"sum={item['features']['sum_red']} span={item['features']['span_red']} "
                      f"odd={item['features']['odd_count']} ac={item['features']['ac_value']} "
                      f"zone={item['features']['zone1_count']}-{item['features']['zone2_count']}"
                      f"-{item['features']['zone3_count']}")
            recommendations.append({
                'red_balls': item['red_balls'],
                'blue_ball': item['blue_ball'],
                'probability': item['score'],          # 兼容 Java PredictResultBo.probability
                'reason': reason,
                'fusion_prob': item['fusion_prob'],
                'aux': item['aux'],
            })

        log(f"[自动预测] 完成，Top-{top_k} 已选出")
        return {
            'status': 'ready',
            'n_candidates': len(candidates),
            'top_k': top_k,
            'recommendations': recommendations,
            'message': f'已从 {len(candidates)} 组候选中筛出 Top-{top_k}'
        }
    except Exception as e:
        import traceback
        log(f"[自动预测错误] {e}")
        return {'status': 'error', 'error': str(e),
                'traceback': traceback.format_exc()}


# ============================== 主循环 ==============================
def main():
    load_all_models()
    model_keys = [k for k in loaded_models if k.endswith('_model')]

    init_signal = {
        'type': 'init_complete',
        'status': 'ready',
        'models_loaded': len(model_keys),
        'model_list': model_keys,
        'history_loaded': len(HISTORY),
        'timestamp': str(np.datetime64('now'))
    }
    print(json.dumps(init_signal, ensure_ascii=False), flush=True)
    log("[就绪] 等待 Java 客户端请求...")

    while True:
        try:
            line = sys.stdin.readline()
            if not line:
                log("[退出] 连接关闭")
                break
            line = line.strip()
            if not line:
                continue
            request = json.loads(line)
            req_type = request.get('type', 'predict')

            if req_type == 'init_check':
                response = handle_init_check(request)
            elif req_type == 'auto_predict':
                response = handle_auto_predict(request)
            elif req_type == 'predict':
                response = handle_prediction(request)
            else:
                response = {'error': f'未知请求类型: {req_type}',
                            'supported_types': ['init_check', 'auto_predict', 'predict']}

            print(json.dumps(response, ensure_ascii=False), flush=True)
        except KeyboardInterrupt:
            log("\n[退出] 收到中断信号")
            break
        except Exception as e:
            log(f"[错误] {e}")
            print(json.dumps({'error': str(e)}, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
