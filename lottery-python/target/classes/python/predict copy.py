#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
双色球完整多层融合预测服务
支持加载和使用真实的LSTM、Transformer、Markov模型
支持初始化信号和自动预测功能
支持通过命令行参数指定模型目录
"""

import os
import sys
import json
import pickle
import warnings
import argparse
import numpy as np

warnings.filterwarnings('ignore')
os.environ['TF_CPP_MIN_LOG_LEVEL'] = '3'

# ============================== 配置 ==============================
def parse_arguments():
    """解析命令行参数"""
    parser = argparse.ArgumentParser(description='双色球预测服务')
    parser.add_argument(
        '--model-dir',
        type=str,
        default=None,
        help='模型文件目录路径，例如：E:\\home\\python\\model'
    )
    return parser.parse_args()

# 解析命令行参数
args = parse_arguments()

# 设置模型目录
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
if args.model_dir:
    MODEL_DIR = os.path.abspath(args.model_dir)
    print(f"[启动] 双色球预测服务 - 完整版", file=sys.stderr, flush=True)
    print(f"[模型目录] 使用指定路径: {MODEL_DIR}", file=sys.stderr, flush=True)
else:
    MODEL_DIR = os.path.join(BASE_DIR, '..', 'models')
    print(f"[启动] 双色球预测服务 - 完整版", file=sys.stderr, flush=True)
    print(f"[模型目录] 使用默认路径: {MODEL_DIR}", file=sys.stderr, flush=True)

# 验证模型目录
if not os.path.exists(MODEL_DIR):
    print(f"[错误] 模型目录不存在: {MODEL_DIR}", file=sys.stderr, flush=True)
    print(f"[建议] 请检查路径或使用 --model-dir 参数指定正确路径", file=sys.stderr, flush=True)
    sys.exit(1)

print(f"[验证] 模型目录存在: ✓", file=sys.stderr, flush=True)

# ============================== 模型加载 ==============================
loaded_models = {}

def load_traditional_ml_models():
    """加载传统ML模型"""
    print("[加载] 传统ML模型...", file=sys.stderr, flush=True)
    
    # RandomForest
    rf_path = os.path.join(MODEL_DIR, 'rf_model.pkl')
    if os.path.exists(rf_path):
        with open(rf_path, 'rb') as f:
            data = pickle.load(f)
            loaded_models['rf_model'] = data['model']
            loaded_models['rf_features'] = data.get('feature_columns', [])
        print("  ✓ rf_model.pkl", file=sys.stderr, flush=True)
    else:
        print(f"  ✗ rf_model.pkl 不存在: {rf_path}", file=sys.stderr, flush=True)
    
    # XGBoost
    xgb_path = os.path.join(MODEL_DIR, 'xgb_model.pkl')
    if os.path.exists(xgb_path):
        with open(xgb_path, 'rb') as f:
            data = pickle.load(f)
            loaded_models['xgb_model'] = data['model']
            loaded_models['xgb_features'] = data.get('feature_columns', [])
        print("  ✓ xgb_model.pkl", file=sys.stderr, flush=True)
    else:
        print(f"  ✗ xgb_model.pkl 不存在: {xgb_path}", file=sys.stderr, flush=True)

def load_lstm_model():
    """加载真实LSTM模型（Keras）"""
    print("[加载] LSTM模型...", file=sys.stderr, flush=True)
    try:
        import tensorflow as tf
        from tensorflow import keras
        
        # 优先尝试新格式 .keras，然后尝试旧格式 .h5
        lstm_model_path = os.path.join(MODEL_DIR, 'lstm_model.keras')
        if not os.path.exists(lstm_model_path):
            lstm_model_path = os.path.join(MODEL_DIR, 'lstm_model.h5')
        
        scaler_path = os.path.join(MODEL_DIR, 'lstm_scaler.pkl')
        
        if os.path.exists(lstm_model_path) and os.path.exists(scaler_path):
            # 加载Keras模型
            model = keras.models.load_model(lstm_model_path)
            
            # 加载Scaler
            with open(scaler_path, 'rb') as f:
                scaler_data = pickle.load(f)
                scaler = scaler_data['scaler']
                n_features = scaler_data['n_features']
                loaded_models['lstm_features'] = scaler_data.get('feature_columns', [])
            
            # 包装器
            class LSTMWrapper:
                def __init__(self, model, scaler, n_features):
                    self.model = model
                    self.scaler = scaler
                    self.n_features = n_features
                
                def predict_proba(self, X):
                    X_scaled = self.scaler.transform(X)
                    # 重塑为 (samples, timesteps, features) 与训练时一致
                    X_reshaped = X_scaled.reshape((X_scaled.shape[0], self.n_features, 1))
                    pred = self.model.predict(X_reshaped, verbose=0)
                    return np.column_stack([1 - pred, pred])
            
            loaded_models['lstm_model'] = LSTMWrapper(model, scaler, n_features)
            model_format = 'keras' if lstm_model_path.endswith('.keras') else 'h5'
            print(f"  ✓ lstm_model.{model_format} (Keras)", file=sys.stderr, flush=True)
        else:
            print("  ✗ LSTM模型文件不存在", file=sys.stderr, flush=True)
            
    except Exception as e:
        print(f"  ✗ LSTM加载失败: {str(e)}", file=sys.stderr, flush=True)

def load_transformer_model():
    """加载真实Transformer模型（PyTorch）"""
    print("[加载] Transformer模型...", file=sys.stderr, flush=True)
    try:
        import torch
        import torch.nn as nn
        
        transformer_model_path = os.path.join(MODEL_DIR, 'transformer_model.pt')
        scaler_path = os.path.join(MODEL_DIR, 'transformer_scaler.pkl')
        
        if os.path.exists(transformer_model_path) and os.path.exists(scaler_path):
            # 定义模型架构（与训练时一致）
            class TransformerClassifier(nn.Module):
                def __init__(self, input_dim, d_model=64, nhead=4, num_layers=2, dropout=0.1):
                    super(TransformerClassifier, self).__init__()
                    
                    self.input_projection = nn.Linear(input_dim, d_model)
                    
                    encoder_layer = nn.TransformerEncoderLayer(
                        d_model=d_model,
                        nhead=nhead,
                        dim_feedforward=128,
                        dropout=dropout,
                        batch_first=True
                    )
                    
                    self.transformer_encoder = nn.TransformerEncoder(
                        encoder_layer,
                        num_layers=num_layers
                    )
                    
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
                    x = self.sigmoid(x)
                    return x
            
            # 加载模型
            checkpoint = torch.load(transformer_model_path, map_location='cpu')
            input_dim = checkpoint['input_dim']
            loaded_models['transformer_features'] = checkpoint.get('feature_columns', [])
            
            device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
            model = TransformerClassifier(input_dim).to(device)
            model.load_state_dict(checkpoint['model_state_dict'])
            model.eval()
            
            # 加载Scaler
            with open(scaler_path, 'rb') as f:
                scaler_data = pickle.load(f)
                scaler = scaler_data['scaler']
            
            # 包装器
            class TransformerWrapper:
                def __init__(self, model, scaler, device):
                    self.model = model
                    self.scaler = scaler
                    self.device = device
                
                def predict_proba(self, X):
                    X_scaled = self.scaler.transform(X)
                    X_tensor = torch.FloatTensor(X_scaled).to(self.device)
                    with torch.no_grad():
                        pred = self.model(X_tensor).cpu().numpy()
                    return np.column_stack([1 - pred, pred])
            
            loaded_models['transformer_model'] = TransformerWrapper(model, scaler, device)
            print("  ✓ transformer_model.pt (PyTorch)", file=sys.stderr, flush=True)
        else:
            print("  ✗ Transformer模型文件不存在", file=sys.stderr, flush=True)
            
    except Exception as e:
        print(f"  ✗ Transformer加载失败: {str(e)}", file=sys.stderr, flush=True)

def load_markov_model():
    """加载真实Markov Chain模型"""
    print("[加载] Markov Chain模型...", file=sys.stderr, flush=True)
    try:
        markov_path = os.path.join(MODEL_DIR, 'markov_model.pkl')
        
        if os.path.exists(markov_path):
            with open(markov_path, 'rb') as f:
                data = pickle.load(f)
                transition_matrix = data['transition_matrix']
                state_to_idx = data['state_to_idx']
                unique_states = data['unique_states']
                n_states = data.get('n_states', len(unique_states))
                loaded_models['markov_features'] = data.get('feature_columns', [])
            
            # 包装器 - 与train_models.py中的MarkovWrapper一致
            class MarkovWrapper:
                def __init__(self, transition_matrix, state_to_idx, unique_states):
                    self.transition_matrix = transition_matrix
                    self.state_to_idx = state_to_idx
                    self.unique_states = unique_states
                
                def predict_proba(self, X):
                    # X是ml_features，需要转换为state_id
                    # 这里使用简化方法：基于特征计算state_id
                    probas = []
                    for features in X:
                        # 使用特征的某种组合来估算state_id
                        # 这里简化为：根据big_count, small_count等计算
                        zone_estimate = int(features[4] * 100 + features[5] * 10)  # big_count, small_count
                        
                        # 找到最接近的状态
                        if zone_estimate in self.state_to_idx:
                            idx = self.state_to_idx[zone_estimate]
                        else:
                            # 使用最接近的状态
                            closest_state = min(self.unique_states, 
                                              key=lambda x: abs(x - zone_estimate))
                            idx = self.state_to_idx[closest_state]
                        
                        probas.append(self.transition_matrix[idx])
                    
                    return np.array(probas)
            
            loaded_models['markov_model'] = MarkovWrapper(
                transition_matrix, state_to_idx, unique_states
            )
            print(f"  ✓ markov_model.pkl ({n_states}个状态)", file=sys.stderr, flush=True)
        else:
            print("  ✗ Markov模型文件不存在", file=sys.stderr, flush=True)
            
    except Exception as e:
        print(f"  ✗ Markov加载失败: {str(e)}", file=sys.stderr, flush=True)

def load_fusion_models():
    """加载融合模型"""
    print("[加载] 融合模型...", file=sys.stderr, flush=True)
    
    # Voting
    voting_path = os.path.join(MODEL_DIR, 'voting_model.pkl')
    if os.path.exists(voting_path):
        with open(voting_path, 'rb') as f:
            data = pickle.load(f)
            loaded_models['voting_model'] = data['model']
            loaded_models['voting_features'] = data.get('feature_columns', [])
        print("  ✓ voting_model.pkl", file=sys.stderr, flush=True)
    
    # Stacking
    stacking_path = os.path.join(MODEL_DIR, 'stacking_model.pkl')
    if os.path.exists(stacking_path):
        with open(stacking_path, 'rb') as f:
            data = pickle.load(f)
            loaded_models['stacking_model'] = data['model']
            loaded_models['stacking_features'] = data.get('feature_columns', [])
        print("  ✓ stacking_model.pkl", file=sys.stderr, flush=True)
    
    # Second Fusion
    second_fusion_path = os.path.join(MODEL_DIR, 'second_fusion_model.pkl')
    if os.path.exists(second_fusion_path):
        with open(second_fusion_path, 'rb') as f:
            data = pickle.load(f)
            loaded_models['second_fusion_model'] = data['model']
            loaded_models['second_fusion_meta_features'] = data.get('meta_feature_names', [])
            loaded_models['second_fusion_weights'] = data.get('weights', [])
        print("  ✓ second_fusion_model.pkl", file=sys.stderr, flush=True)

def load_all_models():
    """加载所有模型"""
    print("\n" + "="*60, file=sys.stderr, flush=True)
    print("开始加载所有模型...", file=sys.stderr, flush=True)
    print("="*60, file=sys.stderr, flush=True)
    
    load_traditional_ml_models()
    load_lstm_model()
    load_transformer_model()
    load_markov_model()
    load_fusion_models()
    
    # 统计已加载的模型
    model_keys = [k for k in loaded_models.keys() if k.endswith('_model')]
    print("\n" + "="*60, file=sys.stderr, flush=True)
    print(f"[就绪] 已加载 {len(model_keys)} 个模型", file=sys.stderr, flush=True)
    if model_keys:
        print(f"[模型列表] {', '.join(model_keys)}", file=sys.stderr, flush=True)
    else:
        print("[警告] 没有成功加载任何模型！", file=sys.stderr, flush=True)
        print(f"[调试] loaded_models 字典内容: {list(loaded_models.keys())}", file=sys.stderr, flush=True)
    print("="*60 + "\n", file=sys.stderr, flush=True)
    print("="*60, file=sys.stderr, flush=True)

# ============================== 预测处理 ==============================
def handle_prediction(request_data):
    """处理预测请求"""
    try:
        model_name = request_data.get('model', 'rf_model')
        features_dict = request_data.get('features', {})
        
        # 特征顺序
        feature_order = [
            'sum_red', 'span_red', 'odd_count', 'even_count',
            'big_count', 'small_count', 'hot_hits', 'cold_hits',
            'blue_hot', 'red_sum_last_diff', 'red_max_last_diff',
            'consecutive_count', 'same_tail_count'
        ]
        
        # 构建特征向量
        X = np.array([[features_dict.get(f, 0) for f in feature_order]])
        
        # 选择模型
        if model_name not in loaded_models:
            return {
                'error': f'模型 {model_name} 未加载',
                'available_models': [k for k in loaded_models.keys() if k.endswith('_model')]
            }
        
        model = loaded_models[model_name]
        
        # 第二层融合模型特殊处理
        if model_name == 'second_fusion_model':
            # 收集所有基础模型和第一层融合模型的预测概率
            meta_feature_names = loaded_models.get('second_fusion_meta_features', [])
            meta_probas = []
            
            for meta_model_name in meta_feature_names:
                if meta_model_name in loaded_models:
                    meta_model = loaded_models[meta_model_name]
                    try:
                        proba = meta_model.predict_proba(X)[0][1]
                        meta_probas.append(proba)
                    except Exception as e:
                        meta_probas.append(0.5)  # 默认值
            
            # 构建元特征
            X_meta = np.array([meta_probas])
            
            # 第二层预测
            probability = float(model.predict_proba(X_meta)[0][1])
            
            # 生成推荐理由
            reason = f"Second_Layer_Fusion_Model(Ensemble_{len(meta_feature_names)}_Models)_Prediction"
            weights = loaded_models.get('second_fusion_weights', [])
            
            if weights and len(weights) == len(meta_feature_names):
                top_contributors = sorted(
                    zip(meta_feature_names, weights, meta_probas),
                    key=lambda x: abs(x[1]),
                    reverse=True
                )[:3]
                
                reason += " | Main_Contributors: "
                reason += ", ".join([
                    f"{name}(weight:{weight:.3f},prob:{prob:.3f})"
                    for name, weight, prob in top_contributors
                ])
        
        else:
            # 普通模型预测
            probability = float(model.predict_proba(X)[0][1])
            
            # 生成推荐理由
            model_desc = {
                'rf_model': 'RandomForest',
                'xgb_model': 'XGBoost',
                'lstm_model': 'LSTM_Deep_Learning',
                'transformer_model': 'Transformer_Attention',
                'markov_model': 'Markov_Chain',
                'voting_model': 'Voting_Ensemble',
                'stacking_model': 'Stacking_Ensemble'
            }.get(model_name, model_name)
            
            reason = f"{model_desc}_Model_Prediction | Probability:{probability:.4f}"
            
            # 添加特征贡献信息（仅针对树模型）
            if model_name in ['rf_model', 'xgb_model'] and hasattr(model, 'feature_importances_'):
                importances = model.feature_importances_
                top_features = sorted(
                    zip(feature_order, importances, [features_dict.get(f, 0) for f in feature_order]),
                    key=lambda x: x[1],
                    reverse=True
                )[:3]
                
                reason += " | Key_Features: "
                reason += ", ".join([
                    f"{feat}={val:.1f}(importance:{imp:.3f})"
                    for feat, imp, val in top_features
                ])
        
        return {
            'probability': probability,
            'reason': reason
        }
        
    except Exception as e:
        import traceback
        return {
            'error': f'预测失败: {str(e)}',
            'traceback': traceback.format_exc()
        }

# ============================== 自动预测功能 ==============================
def handle_auto_predict(request_data):
    """处理自动预测请求 - 用于责任链的AutoPredictChain"""
    try:
        print("[自动预测] 开始生成推荐号码...", file=sys.stderr, flush=True)
        
        # 使用第二层融合模型（最强模型）
        model_name = 'second_fusion_model'
        
        if model_name not in loaded_models:
            print(f"[警告] 第二层融合模型未加载，使用rf_model替代", file=sys.stderr, flush=True)
            model_name = 'rf_model'
        
        # 生成推荐的号码组合
        # 这里可以根据历史数据生成多组候选号码，然后用模型评估
        # 简化版本：返回模型加载状态和可用模型列表
        
        available_models = [k for k in loaded_models.keys() if k.endswith('_model')]
        
        result = {
            'status': 'ready',
            'available_models': available_models,
            'recommended_model': model_name,
            'message': f'预测服务已就绪，共加载{len(available_models)}个模型'
        }
        
        print(f"[自动预测] 完成 - 可用模型: {len(available_models)}个", file=sys.stderr, flush=True)
        
        return result
        
    except Exception as e:
        import traceback
        print(f"[错误] 自动预测失败: {str(e)}", file=sys.stderr, flush=True)
        return {
            'status': 'error',
            'error': f'自动预测失败: {str(e)}',
            'traceback': traceback.format_exc()
        }

# ============================== 初始化检查 ==============================
def handle_init_check(request_data):
    """处理初始化检查请求 - 用于责任链的InitPythonProcessChain"""
    try:
        available_models = [k for k in loaded_models.keys() if k.endswith('_model')]
        
        result = {
            'status': 'initialized',
            'models_loaded': len(available_models),
            'model_list': available_models,
            'message': 'Python预测服务初始化完成'
        }
        
        print(f"[初始化检查] 已加载 {len(available_models)} 个模型", file=sys.stderr, flush=True)
        
        return result
        
    except Exception as e:
        import traceback
        return {
            'status': 'error',
            'error': f'初始化检查失败: {str(e)}',
            'traceback': traceback.format_exc()
        }

# ============================== 主循环 ==============================
def main():
    """主函数 - 长连接模式，支持责任链调用"""
    # 加载所有模型
    load_all_models()
    
    # 统计已加载的模型数量（只统计以_model结尾的键）
    model_keys = [k for k in loaded_models.keys() if k.endswith('_model')]
    models_count = len(model_keys)
    
    # 调试信息
    print(f"\n[调试] 准备发送初始化完成信号", file=sys.stderr, flush=True)
    print(f"[调试] loaded_models 所有键: {list(loaded_models.keys())}", file=sys.stderr, flush=True)
    print(f"[调试] 模型键(以_model结尾): {model_keys}", file=sys.stderr, flush=True)
    print(f"[调试] 模型数量: {models_count}", file=sys.stderr, flush=True)
    
    # 发送初始化完成信号给Java端
    init_signal = {
        'type': 'init_complete',
        'status': 'ready',
        'models_loaded': models_count,
        'model_list': model_keys,  # 添加模型列表
        'timestamp': str(np.datetime64('now'))
    }
    
    print(f"[调试] init_signal = {init_signal}", file=sys.stderr, flush=True)
    print(json.dumps(init_signal, ensure_ascii=False), flush=True)
    print("[就绪] Python预测服务初始化完成，等待Java客户端请求...", file=sys.stderr, flush=True)
    
    # 长连接循环
    while True:
        try:
            line = sys.stdin.readline()
            if not line:
                print("[退出] 连接关闭", file=sys.stderr, flush=True)
                break
            
            line = line.strip()
            if not line:
                continue
            
            # 解析JSON请求
            request = json.loads(line)
            request_type = request.get('type', 'predict')  # 默认为预测请求
            
            # 根据请求类型分发处理
            if request_type == 'init_check':
                # 初始化检查请求（来自InitPythonProcessChain）
                response = handle_init_check(request)
            elif request_type == 'auto_predict':
                # 自动预测请求（来自AutoPredictChain）
                response = handle_auto_predict(request)
            elif request_type == 'predict':
                # 普通预测请求
                response = handle_prediction(request)
            else:
                response = {
                    'error': f'未知的请求类型: {request_type}',
                    'supported_types': ['init_check', 'auto_predict', 'predict']
                }
            
            # 返回JSON响应
            print(json.dumps(response, ensure_ascii=False), flush=True)
            
        except KeyboardInterrupt:
            print("\n[退出] 收到中断信号", file=sys.stderr, flush=True)
            break
        except Exception as e:
            print(f"[错误] {str(e)}", file=sys.stderr, flush=True)
            error_response = {'error': str(e)}
            print(json.dumps(error_response, ensure_ascii=False), flush=True)

if __name__ == "__main__":
    main()
