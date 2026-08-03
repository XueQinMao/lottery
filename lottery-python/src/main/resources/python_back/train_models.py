#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
双色球完整多层融合模型训练脚本 - 优化版
=========================================
本次优化涵盖：
  1. 修复 Markov 模型训练/预测特征对齐问题（统一使用 state_id）
  2. 时间序列顺序切分，避免未来数据泄漏
  3. LSTM / Transformer 真正参与第二层融合（预测阶段可重构序列特征）
  4. 扩展标签为多目标（和值 / 跨度 / 奇偶 / 三区 / 蓝球奇偶 / 蓝球大小）
  5. 候选生成 + 模型打分的自动预测流程（在 predict.py 中实现）
  6. 补充 AC 值、遗漏值、重号、012 路、三区分布等新特征
  7. 回测模块见 backtest.py

数据来源：直接读取 history.csv（原始开奖号码），特征与标签全部由
ssq_features.py 内部计算，不再依赖 Java 端预生成的特征 CSV，
使 ML 流水线自包含、可独立迭代。
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
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
from sklearn.ensemble import RandomForestClassifier, VotingClassifier, StackingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import cross_val_predict

warnings.filterwarnings('ignore')
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'

# Windows 控制台 UTF-8
if sys.platform == 'win32':
    try:
        import io
        sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
        sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')
    except Exception:
        pass

import ssq_features as F

# ============================== 配置 ==============================
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.path.join(BASE_DIR, '..', 'models')
NEG_RATIO = 5          # 负样本 = 历史正样本数 * NEG_RATIO
RANDOM_SEED = 42
TEST_RATIO = 0.2       # 时序测试集比例（最后 20% 期次）


def log(msg):
    print(msg, flush=True)


# ============================== 数据构建 ==============================
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


def build_dataset(history):
    """
    构建训练数据集：
      - 正样本：每期真实开奖（用该期之前的 history 计算动态特征）
      - 负样本：随机号码，挂到一个随机历史位置以获取动态上下文
    返回 DataFrame：22 特征 + 6 标签 + is_positive 标记 + 期号 + 序列/Markov 上下文
    """
    rng = random.Random(RANDOM_SEED)
    rows = []

    # ---- 正样本 ----
    for i in range(1, len(history)):
        reds, blue = history[i]
        prev_reds, prev_blue = history[i - 1]
        red_missing = F.compute_red_missing([history[j][0] for j in range(i)])
        blue_missing = F.compute_blue_missing([history[j][1] for j in range(i)])
        feats = F.compute_features(reds, blue, prev_reds, prev_blue,
                                   red_missing, blue_missing)
        labels = F.compute_labels(reds, blue)
        # 序列上下文（最近 LOOKBACK 期）
        seq_ctx = [history[i - t] for t in range(1, F.LOOKBACK + 1)]
        # Markov 上下文
        state_id = F.markov_state_id(reds)
        prev_state_id = F.markov_state_id(prev_reds)
        row = {**feats, **labels}
        row['is_positive'] = 1
        row['issue_idx'] = i
        row['state_id'] = state_id
        row['prev_state_id'] = prev_state_id
        row['_reds'] = reds
        row['_blue'] = blue
        row['_seq_ctx'] = seq_ctx
        rows.append(row)

    n_pos = len(rows)
    log(f"[数据] 正样本 {n_pos} 条")

    # ---- 负样本 ----
    n_neg = n_pos * NEG_RATIO
    for _ in range(n_neg):
        # 挂到一个随机历史位置，借用其前序上下文
        idx = rng.randint(1, len(history) - 1)
        prev_reds, prev_blue = history[idx - 1]
        red_missing = F.compute_red_missing([history[j][0] for j in range(idx)])
        blue_missing = F.compute_blue_missing([history[j][1] for j in range(idx)])
        reds, blue = F.random_combination(rng)
        feats = F.compute_features(reds, blue, prev_reds, prev_blue,
                                   red_missing, blue_missing)
        labels = F.compute_labels(reds, blue)
        seq_ctx = [history[idx - t] for t in range(1, F.LOOKBACK + 1)]
        state_id = F.markov_state_id(reds)
        prev_state_id = F.markov_state_id(prev_reds)
        row = {**feats, **labels}
        row['is_positive'] = 0
        row['issue_idx'] = idx
        row['state_id'] = state_id
        row['prev_state_id'] = prev_state_id
        row['_reds'] = reds
        row['_blue'] = blue
        row['_seq_ctx'] = seq_ctx
        rows.append(row)

    log(f"[数据] 负样本 {n_neg} 条，总计 {len(rows)} 条")
    return rows


def chronological_split(rows, test_ratio=TEST_RATIO):
    """
    按期号顺序切分：正样本按 issue_idx 排序后取最后 test_ratio 作为测试集；
    负样本按相同比例随机划分（负样本无时序含义）。
    避免未来数据泄漏到训练集。
    """
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
    log(f"[切分] 时序切分 训练 {len(train)} / 测试 {len(test)}（正样本测试集为最后 {len(test_pos)} 期）")
    return train, test


def to_feature_matrix(rows):
    X = np.array([[r[c] for c in F.FEATURE_COLUMNS] for r in rows], dtype=float)
    return X


def to_label_vector(rows, label_name):
    return np.array([r[label_name] for r in rows], dtype=int)


# ============================== 第一步：多目标传统 ML 模型 ==============================
def train_multi_target_ml(train, test):
    """
    对 6 个目标各训一套 RandomForest + XGBoost。
    主目标 label_sum 同时训练 Voting / Stacking。
    返回 base_models 字典。
    """
    import xgboost as xgb

    log("\n" + "=" * 70)
    log("【第一步】多目标传统 ML 模型（RF / XGB）")
    log("=" * 70)

    X_train = to_feature_matrix(train)
    X_test = to_feature_matrix(test)
    log(f"[特征] X_train={X_train.shape}  X_test={X_test.shape}")

    base_models = {}
    for label in F.LABEL_COLUMNS:
        y_train = to_label_vector(train, label)
        y_test = to_label_vector(test, label)
        n_pos = int(y_train.sum())
        if n_pos < 5:
            log(f"[跳过] {label} 正样本过少({n_pos})，跳过")
            continue

        # RF
        rf = RandomForestClassifier(n_estimators=100, max_depth=10,
                                    random_state=RANDOM_SEED, n_jobs=-1)
        rf.fit(X_train, y_train)
        rf_train = rf.score(X_train, y_train)
        rf_test = rf.score(X_test, y_test)
        _save_model(f'rf_{label}.pkl', {'model': rf,
                                        'feature_columns': F.FEATURE_COLUMNS})
        base_models[f'rf_{label}'] = rf

        # XGB
        xgb_clf = xgb.XGBClassifier(n_estimators=100, max_depth=6, learning_rate=0.1,
                                    random_state=RANDOM_SEED, eval_metric='logloss',
                                    use_label_encoder=False)
        xgb_clf.fit(X_train, y_train)
        xgb_train = xgb_clf.score(X_train, y_train)
        xgb_test = xgb_clf.score(X_test, y_test)
        _save_model(f'xgb_{label}.pkl', {'model': xgb_clf,
                                         'feature_columns': F.FEATURE_COLUMNS})
        base_models[f'xgb_{label}'] = xgb_clf

        log(f"  [{label}] RF train/test={rf_train:.4f}/{rf_test:.4f}  "
            f"XGB train/test={xgb_train:.4f}/{xgb_test:.4f}  (pos={n_pos})")

    # 主目标的 Voting / Stacking
    if 'rf_label_sum' in base_models and 'xgb_label_sum' in base_models:
        y_train = to_label_vector(train, F.PRIMARY_LABEL)
        y_test = to_label_vector(test, F.PRIMARY_LABEL)
        estimators = [('rf', base_models['rf_label_sum']),
                      ('xgb', base_models['xgb_label_sum'])]
        voting = VotingClassifier(estimators=estimators, voting='soft')
        voting.fit(X_train, y_train)
        _save_model('voting_model.pkl', {'model': voting,
                                         'feature_columns': F.FEATURE_COLUMNS})
        base_models['voting'] = voting

        stacking = StackingClassifier(estimators=estimators,
                                      final_estimator=LogisticRegression(max_iter=1000),
                                      cv=3)
        stacking.fit(X_train, y_train)
        _save_model('stacking_model.pkl', {'model': stacking,
                                           'feature_columns': F.FEATURE_COLUMNS})
        base_models['stacking'] = stacking
        log(f"  [融合1层] Voting test={voting.score(X_test, y_test):.4f}  "
            f"Stacking test={stacking.score(X_test, y_test):.4f}")

    return base_models, (X_train, X_test, train, test)


# ============================== 第二步：LSTM ==============================
def train_lstm(train, test):
    """训练 LSTM（Keras）预测主目标 label_sum，使用序列特征"""
    log("\n" + "=" * 70)
    log("【第二步】LSTM 模型（Keras/TensorFlow）")
    log("=" * 70)
    try:
        import tensorflow as tf
        from tensorflow import keras
        from tensorflow.keras import layers
    except Exception as e:
        log(f"[跳过] TensorFlow 不可用: {e}")
                return None

    seq_cols = F.sequence_columns()
    X_train = np.array([F.sequence_vector(r['_reds'], r['_blue'], r['_seq_ctx'])
                        for r in train], dtype=float)
    X_test = np.array([F.sequence_vector(r['_reds'], r['_blue'], r['_seq_ctx'])
                       for r in test], dtype=float)
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
    model.fit(X_train_r, y_train, epochs=20, batch_size=32, validation_split=0.2,
              verbose=1, callbacks=[keras.callbacks.EarlyStopping(
                  monitor='val_loss', patience=5, restore_best_weights=True)])

    _, train_acc = model.evaluate(X_train_r, y_train, verbose=0)
    _, test_acc = model.evaluate(X_test_r, y_test, verbose=0)
    log(f"  LSTM train/test={train_acc:.4f}/{test_acc:.4f}")

    model.save(os.path.join(MODEL_DIR, 'lstm_model.keras'))
    with open(os.path.join(MODEL_DIR, 'lstm_scaler.pkl'), 'wb') as f:
        pickle.dump({'scaler': scaler, 'feature_columns': seq_cols,
                     'n_features': n_features}, f)
    log("  ✓ 已保存 lstm_model.keras / lstm_scaler.pkl")
    return {'model': model, 'scaler': scaler, 'n_features': n_features,
            'feature_columns': seq_cols}


# ============================== 第三步：Transformer ==============================
def train_transformer(train, test):
    """训练 Transformer（PyTorch）预测主目标 label_sum"""
    log("\n" + "=" * 70)
    log("【第三步】Transformer 模型（PyTorch）")
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
    X_train = np.array([F.sequence_vector(r['_reds'], r['_blue'], r['_seq_ctx'])
                        for r in train], dtype=float)
    X_test = np.array([F.sequence_vector(r['_reds'], r['_blue'], r['_seq_ctx'])
                       for r in test], dtype=float)
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
        log(f"  Epoch {epoch+1}/20 - loss={total/len(loader):.4f} "
            f"val_loss={te_loss.item():.4f} val_acc={te_acc:.4f}")
        if te_loss.item() < best_loss:
            best_loss = te_loss.item()
            best_state = {k: v.clone() for k, v in model.state_dict().items()}
                patience_counter = 0
            else:
                patience_counter += 1
                if patience_counter >= patience:
                log(f"  Early stopping at epoch {epoch+1}")
                model.load_state_dict(best_state)
                    break
        
        model.eval()
        with torch.no_grad():
        tr_acc = ((model(X_tr) > 0.5).float() == y_tr).float().mean().item()
        te_acc = ((model(X_te) > 0.5).float() == y_te).float().mean().item()
    log(f"  Transformer train/test={tr_acc:.4f}/{te_acc:.4f}")

    torch.save({'model_state_dict': model.state_dict(),
            'input_dim': input_dim,
                'feature_columns': seq_cols},
               os.path.join(MODEL_DIR, 'transformer_model.pt'))
    with open(os.path.join(MODEL_DIR, 'transformer_scaler.pkl'), 'wb') as f:
        pickle.dump({'scaler': scaler, 'feature_columns': seq_cols}, f)
    log("  ✓ 已保存 transformer_model.pt / transformer_scaler.pkl")
    return {'model': model, 'scaler': scaler, 'device': device,
            'feature_columns': seq_cols}


# ============================== 第四步：Markov（特征对齐修复） ==============================
def train_markov(train, test):
    """
    Markov 状态转移模型（修复项 1）：
    训练与预测统一使用 state_id，不再借用 ml_features 字段。
    转移矩阵 transition_matrix[state_idx] = [P(label=0), P(label=1)]，
    label 采用 label_zone（三区分布典型性），与状态语义一致。
    """
    log("\n" + "=" * 70)
    log("【第四步】Markov Chain 模型（特征对齐已修复）")
    log("=" * 70)

    all_rows = train + test
    unique_states = sorted({r['state_id'] for r in all_rows})
    state_to_idx = {s: i for i, s in enumerate(unique_states)}
        n_states = len(unique_states)
    log(f"[状态空间] {n_states} 个唯一状态")

    # 用训练集构建转移统计（按 label_zone）
    trans = np.zeros((n_states, 2))
    counts = np.zeros(n_states)
    for r in train:
        idx = state_to_idx[r['state_id']]
        label = r[F.PRIMARY_LABEL]  # 主目标，便于融合对齐
        trans[idx, label] += 1
        counts[idx] += 1
    # 拉普拉斯平滑
        for i in range(n_states):
        if counts[i] > 0:
            trans[i] = (trans[i] + 1) / (counts[i] + 2)
            else:
            trans[i] = [0.5, 0.5]

    # 评估（测试集）
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
        pickle.dump({'transition_matrix': trans,
                'state_to_idx': state_to_idx,
                'unique_states': unique_states,
                     'n_states': n_states}, f)
    log("  ✓ 已保存 markov_model.pkl")
    return {'transition_matrix': trans, 'state_to_idx': state_to_idx,
            'unique_states': unique_states}


# ============================== 第五步：第二层融合（含 LSTM / Transformer） ==============================
def train_second_fusion(base_models, lstm_info, transformer_info, train, test):
    """
    第二层融合（修复项 3）：将 LSTM / Transformer 的预测概率作为元特征
    一并喂入元学习器，使深度学习模型真正参与融合。
    """
    log("\n" + "=" * 70)
    log("【第五步】第二层融合（含 LSTM / Transformer 元特征）")
    log("=" * 70)

    X_train = to_feature_matrix(train)
    X_test = to_feature_matrix(test)
    y_train = to_label_vector(train, F.PRIMARY_LABEL)
    y_test = to_label_vector(test, F.PRIMARY_LABEL)

    meta_train = []
    meta_test = []
    names = []

    # ---- 传统 ML + 一层融合（主目标）----
    candidates = {
        'rf': base_models.get('rf_label_sum'),
        'xgb': base_models.get('xgb_label_sum'),
        'voting': base_models.get('voting'),
        'stacking': base_models.get('stacking'),
    }
    for name, model in candidates.items():
        if model is None:
            continue
        try:
            tr_proba = cross_val_predict(model, X_train, y_train, cv=3,
                                         method='predict_proba', n_jobs=1)[:, 1]
            te_proba = model.predict_proba(X_test)[:, 1]
            meta_train.append(tr_proba)
            meta_test.append(te_proba)
            names.append(name)
            log(f"  ✓ 元特征 {name}")
    except Exception as e:
            log(f"  ✗ {name}: {e}")

    # ---- Markov（用 state_id 直接查表）----
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
        log("  ✓ 元特征 markov（state_id 对齐）")

    # ---- LSTM ----
    if lstm_info:
        try:
            import tensorflow as tf
            scaler = lstm_info['scaler']
            n_feat = lstm_info['n_features']
            X_tr_seq = np.array([F.sequence_vector(r['_reds'], r['_blue'], r['_seq_ctx'])
                                 for r in train], dtype=float)
            X_te_seq = np.array([F.sequence_vector(r['_reds'], r['_blue'], r['_seq_ctx'])
                                 for r in test], dtype=float)
            X_tr_s = scaler.transform(X_tr_seq).reshape((-1, n_feat, 1))
            X_te_s = scaler.transform(X_te_seq).reshape((-1, n_feat, 1))
            meta_train.append(lstm_info['model'].predict(X_tr_s, verbose=0).flatten())
            meta_test.append(lstm_info['model'].predict(X_te_s, verbose=0).flatten())
            names.append('lstm')
            log("  ✓ 元特征 lstm（序列特征）")
        except Exception as e:
            log(f"  ✗ lstm 元特征: {e}")

    # ---- Transformer ----
    if transformer_info:
        try:
            import torch
            scaler = transformer_info['scaler']
            device = transformer_info['device']
            model = transformer_info['model']
            X_tr_seq = np.array([F.sequence_vector(r['_reds'], r['_blue'], r['_seq_ctx'])
                                 for r in train], dtype=float)
            X_te_seq = np.array([F.sequence_vector(r['_reds'], r['_blue'], r['_seq_ctx'])
                                 for r in test], dtype=float)
            X_tr_s = torch.FloatTensor(scaler.transform(X_tr_seq)).to(device)
            X_te_s = torch.FloatTensor(scaler.transform(X_te_seq)).to(device)
            model.eval()
            with torch.no_grad():
                meta_train.append(model(X_tr_s).cpu().numpy().flatten())
                meta_test.append(model(X_te_s).cpu().numpy().flatten())
            names.append('transformer')
            log("  ✓ 元特征 transformer（序列特征）")
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
        pickle.dump({'model': meta_clf,
                     'meta_feature_names': names,
                     'weights': meta_clf.coef_[0].tolist()}, f)
    log("  ✓ 已保存 second_fusion_model.pkl")


# ============================== 工具 ==============================
def _save_model(filename, data):
    os.makedirs(MODEL_DIR, exist_ok=True)
    with open(os.path.join(MODEL_DIR, filename), 'wb') as f:
        pickle.dump(data, f)


# ============================== 主函数 ==============================
def parse_arguments():
    parser = argparse.ArgumentParser(description='双色球多层融合模型训练脚本（优化版）')
    parser.add_argument('--history', required=False,
                        default=os.path.join(BASE_DIR, '..', '..', '..', 'history.csv'),
                        help='history.csv 路径（Python 直接读取，特征内部计算）')
    parser.add_argument('--model-dir', required=False, default=None,
                        help='模型保存目录，默认 ../models')
    # 兼容 Java TrainModelServiceImpl 传入的旧参数（接受但忽略，
    # 新版特征由 ssq_features.py 基于 history.csv 内部计算）
    parser.add_argument('--ml-features', required=False, default=None,
                        help='[已弃用/兼容] ml_features.csv 路径，新版不再读取')
    parser.add_argument('--sequence-features', required=False, default=None,
                        help='[已弃用/兼容] sequence_features.csv 路径，新版不再读取')
    parser.add_argument('--markov-features', required=False, default=None,
                        help='[已弃用/兼容] markov_features.csv 路径，新版不再读取')
    return parser.parse_args()


def main():
    global MODEL_DIR
    args = parse_arguments()
    if args.model_dir:
        MODEL_DIR = os.path.abspath(args.model_dir)
        os.makedirs(MODEL_DIR, exist_ok=True)

    log("\n" + "=" * 70)
    log(f" 双色球多层融合模型训练（优化版）  {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    log(f" MODEL_DIR={MODEL_DIR}")
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

    # 保存元信息
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
    log("  多目标 RF/XGB: rf_label_*.pkl / xgb_label_*.pkl")
    log("  一层融合: voting_model.pkl / stacking_model.pkl")
    log("  深度学习: lstm_model.keras / transformer_model.pt")
    log("  Markov: markov_model.pkl")
    log("  二层融合: second_fusion_model.pkl")
    log("  元信息: meta.pkl")
    log("=" * 70)


if __name__ == "__main__":
    main()
