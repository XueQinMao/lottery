#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
双色球完整多层融合模型训练脚本 - 优化版
=========================================
要点：
  1. 主监督目标 = is_positive（真实开奖 vs 随机票），不再用可从特征确定性推出的 label_sum
  2. 典型性 LABEL_COLUMNS 仅训练辅模型，供分析/兼容
  3. 时间序列切分，避免未来泄漏；LOOKBACK 序列上下文不做负索引回绕
  4. 二层融合：传统 ML 用 OOF；LSTM/Transformer 用时序 holdout 预测作元特征
  5. 特征全部由 ssq_features 基于 history.csv 计算（Java 侧 ml/sequence/markov CSV 已弃用）
"""

import os
import sys
import pickle
import warnings
import argparse
import random
import numpy as np
import pandas as pd
from datetime import datetime
from sklearn.preprocessing import StandardScaler
from sklearn.ensemble import RandomForestClassifier, VotingClassifier, StackingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import cross_val_predict

warnings.filterwarnings('ignore')
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'

if sys.platform == 'win32':
    try:
        import io
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
        sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
    except Exception:
        pass

import ssq_features as F

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.path.join(BASE_DIR, '..', 'models')
NEG_RATIO = 5
RANDOM_SEED = 42
TEST_RATIO = 0.2
# 二层融合中 DL 元特征：用训练期最后一段作 holdout，降低 in-sample 泄漏
DL_META_HOLDOUT_RATIO = 0.2


def log(msg):
    print(msg, flush=True)


def load_history(history_path):
    """读取 history.csv，返回按期号升序的 (reds, blue) 列表"""
    df = pd.read_csv(history_path)
    df = df.sort_values('issue').reset_index(drop=True)
    log(f"[历史] 读取 {len(df)} 期开奖记录: {history_path}")
    records = []
    for _, row in df.iterrows():
        reds = [int(row[f'red{i}']) for i in range(1, 7)]
        blue = int(row['blue'])
        records.append((reds, blue))
    return records


def build_seq_ctx(history, i):
    """
    取当期之前最近 LOOKBACK 期（newest -> oldest），不足时用最早一期填充，
    禁止 Python 负索引回绕到历史末尾造成泄漏。
    """
    ctx = []
    for t in range(1, F.LOOKBACK + 1):
        j = i - t
        if j >= 0:
            ctx.append(history[j])
        else:
            ctx.append(history[0])
    return ctx


def build_dataset(history):
    """
    正样本：真实开奖；负样本：随机号码挂到随机历史位置。
    主标签 is_positive；辅标签为形态典型性（不参与主融合监督）。
    """
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
        row['prev_state_id'] = F.markov_state_id(prev_reds)
        row['_reds'] = reds
        row['_blue'] = blue
        row['_seq_ctx'] = build_seq_ctx(history, i)
        rows.append(row)

    n_pos = len(rows)
    log(f"[数据] 正样本 {n_pos} 条")

    n_neg = n_pos * NEG_RATIO
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
        row['prev_state_id'] = F.markov_state_id(prev_reds)
        row['_reds'] = reds
        row['_blue'] = blue
        row['_seq_ctx'] = build_seq_ctx(history, idx)
        rows.append(row)

    log(f"[数据] 负样本 {n_neg} 条，总计 {len(rows)} 条；主标签={F.PRIMARY_LABEL}")
    return rows


def chronological_split(rows, test_ratio=TEST_RATIO):
    positives = [r for r in rows if r['is_positive'] == 1]
    negatives = [r for r in rows if r['is_positive'] == 0]
    positives.sort(key=lambda r: r['issue_idx'])

    split_idx = int(len(positives) * (1 - test_ratio))
    train_pos = positives[:split_idx]
    test_pos = positives[split_idx:]

    rng = random.Random(RANDOM_SEED)
    rng.shuffle(negatives)
    neg_split = int(len(negatives) * (1 - test_ratio))
    train_neg = negatives[:neg_split]
    test_neg = negatives[neg_split:]

    train = train_pos + train_neg
    test = test_pos + test_neg
    rng.shuffle(train)
    rng.shuffle(test)
    log(f"[切分] 训练 {len(train)} / 测试 {len(test)}（正样本测试最后 {len(test_pos)} 期）")
    return train, test


def to_feature_matrix(rows):
    return np.array([[r[c] for c in F.FEATURE_COLUMNS] for r in rows], dtype=float)


def to_label_vector(rows, label_name):
    return np.array([r[label_name] for r in rows], dtype=int)


def to_seq_matrix(rows):
    return np.array([F.sequence_vector(r['_reds'], r['_blue'], r['_seq_ctx'])
                     for r in rows], dtype=float)


def primary_key(prefix):
    return f'{prefix}_{F.PRIMARY_LABEL}'


# ============================== 多目标 ML ==============================
def train_multi_target_ml(train, test):
    """
    辅目标：LABEL_COLUMNS（典型性）
    主目标：PRIMARY_LABEL=is_positive → Voting / Stacking
    """
    import xgboost as xgb

    log("\n" + "=" * 70)
    log("【第一步】多目标 ML（辅=典型性，主=is_positive）")
    log("=" * 70)

    X_train = to_feature_matrix(train)
    X_test = to_feature_matrix(test)
    log(f"[特征] X_train={X_train.shape}  X_test={X_test.shape}")

    base_models = {}
    labels_to_train = list(F.LABEL_COLUMNS) + [F.PRIMARY_LABEL]
    # 去重且保持顺序
    seen = set()
    labels_to_train = [l for l in labels_to_train if not (l in seen or seen.add(l))]

    for label in labels_to_train:
        y_train = to_label_vector(train, label)
        y_test = to_label_vector(test, label)
        n_pos = int(y_train.sum())
        if n_pos < 5:
            log(f"[跳过] {label} 正样本过少({n_pos})")
            continue

        rf = RandomForestClassifier(n_estimators=100, max_depth=10,
                                    random_state=RANDOM_SEED, n_jobs=-1)
        rf.fit(X_train, y_train)
        _save_model(f'rf_{label}.pkl', {
            'model': rf, 'feature_columns': F.FEATURE_COLUMNS, 'label': label})
        base_models[f'rf_{label}'] = rf

        xgb_clf = xgb.XGBClassifier(
            n_estimators=100, max_depth=6, learning_rate=0.1,
            random_state=RANDOM_SEED, eval_metric='logloss',
            use_label_encoder=False)
        xgb_clf.fit(X_train, y_train)
        _save_model(f'xgb_{label}.pkl', {
            'model': xgb_clf, 'feature_columns': F.FEATURE_COLUMNS, 'label': label})
        base_models[f'xgb_{label}'] = xgb_clf

        log(f"  [{label}] RF={rf.score(X_train, y_train):.4f}/{rf.score(X_test, y_test):.4f}  "
            f"XGB={xgb_clf.score(X_train, y_train):.4f}/{xgb_clf.score(X_test, y_test):.4f}  "
            f"(pos={n_pos})")

    rf_key, xgb_key = primary_key('rf'), primary_key('xgb')
    if rf_key in base_models and xgb_key in base_models:
        y_train = to_label_vector(train, F.PRIMARY_LABEL)
        y_test = to_label_vector(test, F.PRIMARY_LABEL)
        estimators = [('rf', base_models[rf_key]), ('xgb', base_models[xgb_key])]
        voting = VotingClassifier(estimators=estimators, voting='soft')
        voting.fit(X_train, y_train)
        _save_model('voting_model.pkl', {
            'model': voting, 'feature_columns': F.FEATURE_COLUMNS,
            'label': F.PRIMARY_LABEL})
        base_models['voting'] = voting

        stacking = StackingClassifier(
            estimators=estimators,
            final_estimator=LogisticRegression(max_iter=1000),
            cv=3)
        stacking.fit(X_train, y_train)
        _save_model('stacking_model.pkl', {
            'model': stacking, 'feature_columns': F.FEATURE_COLUMNS,
            'label': F.PRIMARY_LABEL})
        base_models['stacking'] = stacking
        log(f"  [融合1层] Voting={voting.score(X_test, y_test):.4f}  "
            f"Stacking={stacking.score(X_test, y_test):.4f}")

    return base_models, (X_train, X_test, train, test)


# ============================== LSTM ==============================
def train_lstm(train, test):
    log("\n" + "=" * 70)
    log("【第二步】LSTM（主目标 is_positive）")
    log("=" * 70)
    try:
        from tensorflow import keras
        from tensorflow.keras import layers
    except Exception as e:
        log(f"[跳过] TensorFlow 不可用: {e}")
        return None

    seq_cols = F.sequence_columns()
    X_train = to_seq_matrix(train)
    X_test = to_seq_matrix(test)
    y_train = to_label_vector(train, F.PRIMARY_LABEL)
    y_test = to_label_vector(test, F.PRIMARY_LABEL)

    scaler = StandardScaler()
    X_train_s = scaler.fit_transform(X_train)
    X_test_s = scaler.transform(X_test)
    n_features = X_train_s.shape[1]
    X_train_r = X_train_s.reshape((X_train_s.shape[0], n_features, 1))
    X_test_r = X_test_s.reshape((X_test_s.shape[0], n_features, 1))

    model = keras.Sequential([
        layers.LSTM(64, return_sequences=True, input_shape=(n_features, 1)),
        layers.Dropout(0.2),
        layers.LSTM(32, return_sequences=False),
        layers.Dropout(0.2),
        layers.Dense(16, activation='relu'),
        layers.Dropout(0.1),
        layers.Dense(1, activation='sigmoid')
    ])
    model.compile(optimizer='adam', loss='binary_crossentropy', metrics=['accuracy'])
    model.fit(
        X_train_r, y_train, epochs=20, batch_size=32, validation_split=0.2, verbose=1,
        callbacks=[keras.callbacks.EarlyStopping(
            monitor='val_loss', patience=5, restore_best_weights=True)])

    _, train_acc = model.evaluate(X_train_r, y_train, verbose=0)
    _, test_acc = model.evaluate(X_test_r, y_test, verbose=0)
    log(f"  LSTM train/test={train_acc:.4f}/{test_acc:.4f}")

    model.save(os.path.join(MODEL_DIR, 'lstm_model.keras'))
    with open(os.path.join(MODEL_DIR, 'lstm_scaler.pkl'), 'wb') as f:
        pickle.dump({'scaler': scaler, 'feature_columns': seq_cols,
                     'n_features': n_features, 'label': F.PRIMARY_LABEL}, f)
    log("  ✓ 已保存 lstm_model.keras / lstm_scaler.pkl")
    return {'model': model, 'scaler': scaler, 'n_features': n_features,
            'feature_columns': seq_cols}


# ============================== Transformer ==============================
def train_transformer(train, test):
    log("\n" + "=" * 70)
    log("【第三步】Transformer（主目标 is_positive）")
    log("=" * 70)
    try:
        import torch
        import torch.nn as nn
        import torch.optim as optim
        from torch.utils.data import TensorDataset, DataLoader
    except Exception as e:
        log(f"[跳过] PyTorch 不可用: {e}")
        return None

    seq_cols = F.sequence_columns()
    X_train = to_seq_matrix(train)
    X_test = to_seq_matrix(test)
    y_train = to_label_vector(train, F.PRIMARY_LABEL)
    y_test = to_label_vector(test, F.PRIMARY_LABEL)

    scaler = StandardScaler()
    X_train_s = scaler.fit_transform(X_train)
    X_test_s = scaler.transform(X_test)
    input_dim = X_train_s.shape[1]

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

    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    model = TransformerClassifier(input_dim).to(device)
    X_tr = torch.FloatTensor(X_train_s).to(device)
    y_tr = torch.FloatTensor(y_train).unsqueeze(1).to(device)
    X_te = torch.FloatTensor(X_test_s).to(device)
    y_te = torch.FloatTensor(y_test).unsqueeze(1).to(device)
    loader = DataLoader(TensorDataset(X_tr, y_tr), batch_size=32, shuffle=True)

    criterion = nn.BCELoss()
    optimizer = optim.Adam(model.parameters(), lr=0.001)
    best_loss, patience, patience_counter = float('inf'), 5, 0
    best_state = None
    for epoch in range(20):
        model.train()
        total = 0.0
        for bx, by in loader:
            optimizer.zero_grad()
            out = model(bx)
            loss = criterion(out, by)
            loss.backward()
            optimizer.step()
            total += loss.item()
        model.eval()
        with torch.no_grad():
            te_out = model(X_te)
            te_loss = criterion(te_out, y_te)
            te_acc = ((te_out > 0.5).float() == y_te).float().mean().item()
        log(f"  Epoch {epoch + 1}/20 - loss={total / len(loader):.4f} "
            f"val_loss={te_loss.item():.4f} val_acc={te_acc:.4f}")
        if te_loss.item() < best_loss:
            best_loss = te_loss.item()
            best_state = {k: v.clone() for k, v in model.state_dict().items()}
            patience_counter = 0
        else:
            patience_counter += 1
            if patience_counter >= patience:
                log(f"  Early stopping at epoch {epoch + 1}")
                if best_state is not None:
                    model.load_state_dict(best_state)
                break

    model.eval()
    with torch.no_grad():
        tr_acc = ((model(X_tr) > 0.5).float() == y_tr).float().mean().item()
        te_acc = ((model(X_te) > 0.5).float() == y_te).float().mean().item()
    log(f"  Transformer train/test={tr_acc:.4f}/{te_acc:.4f}")

    torch.save({
        'model_state_dict': model.state_dict(),
        'input_dim': input_dim,
        'feature_columns': seq_cols,
        'label': F.PRIMARY_LABEL,
    }, os.path.join(MODEL_DIR, 'transformer_model.pt'))
    with open(os.path.join(MODEL_DIR, 'transformer_scaler.pkl'), 'wb') as f:
        pickle.dump({'scaler': scaler, 'feature_columns': seq_cols,
                     'label': F.PRIMARY_LABEL}, f)
    log("  ✓ 已保存 transformer_model.pt / transformer_scaler.pkl")
    return {'model': model, 'scaler': scaler, 'device': device,
            'feature_columns': seq_cols}


# ============================== Markov ==============================
def train_markov(train, test):
    """
    按 state_id 统计主标签 is_positive 的先验（拉普拉斯平滑）。
    命名保留 Markov，语义为状态条件正样本率，供融合元特征使用。
    """
    log("\n" + "=" * 70)
    log("【第四步】状态先验模型（state_id → P(is_positive)）")
    log("=" * 70)

    all_rows = train + test
    unique_states = sorted({r['state_id'] for r in all_rows})
    state_to_idx = {s: i for i, s in enumerate(unique_states)}
    n_states = len(unique_states)
    log(f"[状态空间] {n_states} 个唯一状态")

    trans = np.zeros((n_states, 2))
    counts = np.zeros(n_states)
    for r in train:
        idx = state_to_idx[r['state_id']]
        label = int(r[F.PRIMARY_LABEL])
        trans[idx, label] += 1
        counts[idx] += 1
    for i in range(n_states):
        if counts[i] > 0:
            trans[i] = (trans[i] + 1) / (counts[i] + 2)
        else:
            trans[i] = [0.5, 0.5]

    correct = 0
    for r in test:
        idx = state_to_idx.get(r['state_id'])
        if idx is None:
            continue
        pred = 1 if trans[idx, 1] > 0.5 else 0
        if pred == r[F.PRIMARY_LABEL]:
            correct += 1
    log(f"  Markov test acc={correct / max(len(test), 1):.4f}")

    with open(os.path.join(MODEL_DIR, 'markov_model.pkl'), 'wb') as f:
        pickle.dump({
            'transition_matrix': trans,
            'state_to_idx': state_to_idx,
            'unique_states': unique_states,
            'n_states': n_states,
            'label': F.PRIMARY_LABEL,
        }, f)
    log("  ✓ 已保存 markov_model.pkl")
    return {'transition_matrix': trans, 'state_to_idx': state_to_idx,
            'unique_states': unique_states}


def _chronological_holdout_indices(rows, holdout_ratio=DL_META_HOLDOUT_RATIO):
    """按 issue_idx 将样本划分为 fit / holdout（用于 DL 元特征）。"""
    indexed = list(enumerate(rows))
    indexed.sort(key=lambda x: x[1]['issue_idx'])
    cut = int(len(indexed) * (1 - holdout_ratio))
    fit_idx = [i for i, _ in indexed[:cut]]
    hold_idx = [i for i, _ in indexed[cut:]]
    if not hold_idx:
        hold_idx = fit_idx[-max(1, len(fit_idx) // 5):]
        fit_idx = [i for i in fit_idx if i not in set(hold_idx)]
    return fit_idx, hold_idx


def _dl_meta_probas(predict_fn, n_samples, fit_idx, hold_idx, hold_probas):
    """
    拟合段填 0.5（避免 in-sample），holdout 段用真实预测；
    测试集由调用方单独预测。返回长度为 n_samples 的 train 元特征。
    """
    out = np.full(n_samples, 0.5, dtype=float)
    for i, p in zip(hold_idx, hold_probas):
        out[i] = float(p)
    return out


# ============================== 二层融合 ==============================
def train_second_fusion(base_models, lstm_info, transformer_info, train, test):
    log("\n" + "=" * 70)
    log("【第五步】第二层融合（主目标 is_positive）")
    log("=" * 70)

    X_train = to_feature_matrix(train)
    X_test = to_feature_matrix(test)
    y_train = to_label_vector(train, F.PRIMARY_LABEL)
    y_test = to_label_vector(test, F.PRIMARY_LABEL)

    meta_train = []
    meta_test = []
    names = []

    candidates = {
        'rf': base_models.get(primary_key('rf')),
        'xgb': base_models.get(primary_key('xgb')),
        'voting': base_models.get('voting'),
        'stacking': base_models.get('stacking'),
    }
    for name, model in candidates.items():
        if model is None:
            continue
        try:
            tr_proba = cross_val_predict(
                model, X_train, y_train, cv=3, method='predict_proba', n_jobs=1)[:, 1]
            te_proba = model.predict_proba(X_test)[:, 1]
            meta_train.append(tr_proba)
            meta_test.append(te_proba)
            names.append(name)
            log(f"  ✓ 元特征 {name} (OOF)")
        except Exception as e:
            log(f"  ✗ {name}: {e}")

    markov = base_models.get('markov')
    if markov:
        def markov_proba(rows):
            probs = []
            for r in rows:
                idx = markov['state_to_idx'].get(r['state_id'])
                probs.append(markov['transition_matrix'][idx, 1]
                             if idx is not None else 0.5)
            return np.array(probs)

        meta_train.append(markov_proba(train))
        meta_test.append(markov_proba(test))
        names.append('markov')
        log("  ✓ 元特征 markov")

    fit_idx, hold_idx = _chronological_holdout_indices(train)

    if lstm_info:
        try:
            scaler = lstm_info['scaler']
            n_feat = lstm_info['n_features']
            model = lstm_info['model']
            X_tr_seq = to_seq_matrix(train)
            X_te_seq = to_seq_matrix(test)
            X_hold = scaler.transform(X_tr_seq[hold_idx]).reshape((-1, n_feat, 1))
            hold_p = model.predict(X_hold, verbose=0).flatten()
            tr_meta = _dl_meta_probas(None, len(train), fit_idx, hold_idx, hold_p)
            te_meta = model.predict(
                scaler.transform(X_te_seq).reshape((-1, n_feat, 1)), verbose=0).flatten()
            meta_train.append(tr_meta)
            meta_test.append(te_meta)
            names.append('lstm')
            log(f"  ✓ 元特征 lstm（holdout={len(hold_idx)}，fit 段填 0.5）")
        except Exception as e:
            log(f"  ✗ lstm 元特征: {e}")

    if transformer_info:
        try:
            import torch
            scaler = transformer_info['scaler']
            device = transformer_info['device']
            model = transformer_info['model']
            X_tr_seq = to_seq_matrix(train)
            X_te_seq = to_seq_matrix(test)
            model.eval()
            with torch.no_grad():
                X_hold = torch.FloatTensor(
                    scaler.transform(X_tr_seq[hold_idx])).to(device)
                hold_p = model(X_hold).cpu().numpy().flatten()
                te_t = torch.FloatTensor(scaler.transform(X_te_seq)).to(device)
                te_meta = model(te_t).cpu().numpy().flatten()
            tr_meta = _dl_meta_probas(None, len(train), fit_idx, hold_idx, hold_p)
            meta_train.append(tr_meta)
            meta_test.append(te_meta)
            names.append('transformer')
            log(f"  ✓ 元特征 transformer（holdout={len(hold_idx)}，fit 段填 0.5）")
        except Exception as e:
            log(f"  ✗ transformer 元特征: {e}")

    if len(meta_train) < 2:
        log("[警告] 可用元特征不足 2 个，跳过第二层融合")
        return

    X_train_meta = np.column_stack(meta_train)
    X_test_meta = np.column_stack(meta_test)
    log(f"[元特征] shape={X_train_meta.shape}  模型={names}")

    meta_clf = LogisticRegression(max_iter=1000, random_state=RANDOM_SEED)
    meta_clf.fit(X_train_meta, y_train)
    log(f"  第二层融合 train/test={meta_clf.score(X_train_meta, y_train):.4f}"
        f"/{meta_clf.score(X_test_meta, y_test):.4f}")
    log("  模型权重:")
    for name, w in zip(names, meta_clf.coef_[0]):
        log(f"    {name:12s}: {w:8.4f}")

    with open(os.path.join(MODEL_DIR, 'second_fusion_model.pkl'), 'wb') as f:
        pickle.dump({
            'model': meta_clf,
            'meta_feature_names': names,
            'weights': meta_clf.coef_[0].tolist(),
            'label': F.PRIMARY_LABEL,
            'default_proba': 0.5,
        }, f)
    log("  ✓ 已保存 second_fusion_model.pkl")


def _save_model(filename, data):
    os.makedirs(MODEL_DIR, exist_ok=True)
    with open(os.path.join(MODEL_DIR, filename), 'wb') as f:
        pickle.dump(data, f)


def parse_arguments():
    parser = argparse.ArgumentParser(description='双色球多层融合模型训练脚本（优化版）')
    parser.add_argument('--history', required=False,
                        default=os.path.join(BASE_DIR, '..', '..', '..', 'history.csv'),
                        help='history.csv 路径')
    parser.add_argument('--model-dir', required=False, default=None,
                        help='模型保存目录，默认 ../models')
    parser.add_argument('--ml-features', required=False, default=None,
                        help='[已弃用/兼容] 新版不再读取')
    parser.add_argument('--sequence-features', required=False, default=None,
                        help='[已弃用/兼容] 新版不再读取')
    parser.add_argument('--markov-features', required=False, default=None,
                        help='[已弃用/兼容] 新版不再读取')
    return parser.parse_args()


def main():
    global MODEL_DIR
    args = parse_arguments()
    if args.model_dir:
        MODEL_DIR = os.path.abspath(args.model_dir)
        os.makedirs(MODEL_DIR, exist_ok=True)

    log("\n" + "=" * 70)
    log(f" 双色球多层融合模型训练  {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    log(f" MODEL_DIR={MODEL_DIR}  PRIMARY_LABEL={F.PRIMARY_LABEL}")
    log("=" * 70)

    history_path = args.history
    if not os.path.exists(history_path):
        log(f"[错误] history.csv 不存在: {history_path}")
        sys.exit(1)

    history = load_history(history_path)
    rows = build_dataset(history)
    train, test = chronological_split(rows)

    base_models, _ = train_multi_target_ml(train, test)
    lstm_info = train_lstm(train, test)
    transformer_info = train_transformer(train, test)
    markov_info = train_markov(train, test)
    base_models['markov'] = markov_info

    train_second_fusion(base_models, lstm_info, transformer_info, train, test)

    with open(os.path.join(MODEL_DIR, 'meta.pkl'), 'wb') as f:
        pickle.dump({
            'feature_columns': F.FEATURE_COLUMNS,
            'label_columns': F.LABEL_COLUMNS,
            'primary_label': F.PRIMARY_LABEL,
            'lookback': F.LOOKBACK,
            'sum_typical': F.SUM_TYPICAL,
            'span_typical': F.SPAN_TYPICAL,
            'odd_typical': list(F.ODD_TYPICAL),
            'created_at': datetime.now().isoformat(),
        }, f)

    log("\n" + "=" * 70)
    log(" 训练完成。模型清单：")
    log(f"  主目标({F.PRIMARY_LABEL}): rf/xgb_{F.PRIMARY_LABEL}.pkl + voting/stacking")
    log("  辅目标(典型性): rf_label_*.pkl / xgb_label_*.pkl")
    log("  深度学习: lstm_model.keras / transformer_model.pt")
    log("  状态先验: markov_model.pkl")
    log("  二层融合: second_fusion_model.pkl")
    log("=" * 70)


if __name__ == "__main__":
    main()
