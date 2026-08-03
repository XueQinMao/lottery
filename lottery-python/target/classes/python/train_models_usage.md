# 双色球预测模型 - 使用说明（优化版）

> 本版本对训练 / 预测 / 回测做了 7 项优化：
> ①修复 Markov 特征对齐 ②时序顺序切分 ③LSTM/Transformer 进入第二层融合
> ④多目标标签 ⑤候选生成+模型打分 ⑥补充 AC 值/遗漏值等特征 ⑦回测模块

## 一、模块概览

| 脚本 | 作用 |
|------|------|
| `ssq_features.py` | **特征与标签权威实现**（训练/预测/回测共用） |
| `train_models.py` | 多层融合模型训练（读取 history.csv） |
| `predict.py` | 长连接预测服务（Java 通过 stdin/stdout 调用） |
| `backtest.py` | walk-forward 回测，评估各目标命中率 |

特征工程已统一收敛到 `ssq_features.py`，不再依赖 Java 预生成的特征 CSV；
Java 端只需保证 `history.csv`（原始开奖号码）可用即可。

## 二、特征列（FEATURE_COLUMNS，共 22 维）

```
sum_red, span_red, odd_count, even_count, big_count, small_count,
hot_hits, cold_hits, blue_hot, red_sum_last_diff, red_max_last_diff,
consecutive_count, same_tail_count,
ac_value, repeat_from_last, max_missing, avg_missing,
blue_missing, blue_012_road, zone1_count, zone2_count, zone3_count
```

新增特征说明：
- `ac_value`：AC 值（算术复杂度），衡量号码组合随机性
- `repeat_from_last`：与上一期重号个数
- `max_missing / avg_missing`：6 个红球的最大 / 平均遗漏期数
- `blue_missing`：蓝球遗漏期数
- `blue_012_road`：蓝球 012 路（blue % 3）
- `zone1/2/3_count`：红球三区（1-11 / 12-22 / 23-33）分布

## 三、多目标标签（LABEL_COLUMNS，共 6 个）

| 标签 | 典型区间 |
|------|---------|
| `label_sum` | 红球和值 90~120 |
| `label_span` | 红球跨度 16~28 |
| `label_odd_even` | 奇数个数 ∈ {2,3,4} |
| `label_zone` | 三区每区 1~4 个（均衡） |
| `label_blue_odd` | 蓝球为奇数 |
| `label_blue_big` | 蓝球 ≥ 9 |

`PRIMARY_LABEL = label_sum`，作为第二层融合主目标；其余标签用于候选多准则打分。

## 四、train_models.py

### 命令行参数

| 参数 | 必需 | 说明 |
|------|------|------|
| `--history` | 否 | `history.csv` 路径（默认脚本同级向上查找） |
| `--model-dir` | 否 | 模型保存目录（默认 `../models`） |

### 使用示例

```bash
# 默认路径
python train_models.py --history E:\home\python\history.csv

# 指定模型目录
python train_models.py --history E:\home\python\history.csv --model-dir E:\home\python\model
```

### 输出模型文件

| 文件 | 说明 |
|------|------|
| `rf_label_*.pkl` / `xgb_label_*.pkl` | 6 个目标各自的 RF / XGBoost |
| `voting_model.pkl` / `stacking_model.pkl` | 主目标一层融合 |
| `lstm_model.keras` + `lstm_scaler.pkl` | LSTM（序列特征） |
| `transformer_model.pt` + `transformer_scaler.pkl` | Transformer（序列特征） |
| `markov_model.pkl` | Markov 状态转移（state_id 对齐） |
| `second_fusion_model.pkl` | 第二层融合（含 LSTM/Transformer 元特征） |
| `meta.pkl` | 特征/标签/典型区间元信息 |

## 五、predict.py（长连接服务）

### 启动参数

| 参数 | 说明 |
|------|------|
| `--model-dir` | 模型目录 |
| `--data-dir` | `history.csv` 所在目录（默认 model-dir 父目录） |

### 请求协议（stdin JSON）

**init_check** —— 初始化检查
```json
{"type": "init_check"}
```

**predict** —— 单组号码预测（新协议，推荐）
```json
{"type": "predict", "model": "second_fusion_model",
 "red_balls": [3, 11, 15, 22, 27, 31], "blue_ball": 9}
```
兼容旧协议（仅传 features 字典，动态特征将退化为 0）：
```json
{"type": "predict", "model": "second_fusion_model", "features": {...}}
```
响应（保持向后兼容，供 Java `PredictResultBo` 解析）：
```json
{"probability": 0.7321, "reason": "Second_Layer_Fusion(...) | Main: ..."}
```

**auto_predict** —— 候选生成 + 模型打分（实现项 5）
```json
{"type": "auto_predict", "n_candidates": 2000, "top_k": 20, "hot_weight": 0.6}
```
响应：
```json
{"status": "ready", "n_candidates": 2000, "top_k": 20,
 "recommendations": [
   {"red_balls": [...], "blue_ball": 9, "probability": 0.65,
    "reason": "score=... fusion=... sum=... span=...", "fusion_prob": 0.7, "aux": {...}}
 ]}
```

## 六、backtest.py（回测）

```bash
python backtest.py \
  --history E:\home\python\history.csv \
  --start 0 --end 50 --step 5 \
  --threshold 0.5 --top-k 20 --n-candidates 2000 \
  --output backtest_result.json
```

输出每个目标的 Precision / Recall / F1，以及候选生成 Top-K 对真实开奖的命中统计
（红球全中 / 红5 / 红4 / 蓝球命中）。

## 七、Java 端调用变化

- `PredictServiceImpl` 现直接传 `red_balls` + `blue_ball` 给 Python，
  特征由 Python 基于 `history.csv` 内部计算（含动态特征）。
- `FeatureCalculatorServiceImpl` 仍生成 4 个 CSV（供分析/兼容），
  `ml_features.csv` 已同步为 22 特征 + 6 标签格式。

## 八、Python 依赖

```bash
pip install numpy pandas scikit-learn xgboost tensorflow torch
```

## 九、注意事项

1. **history.csv 必须按期号升序**（Java 端已做排序），否则时序特征/遗漏值错误。
2. 深度学习模型（LSTM/Transformer）首次训练较慢，无 GPU 时可耐心等待；
   缺少 TensorFlow/PyTorch 时训练脚本会自动跳过对应模型，不阻断流程。
3. 第二层融合的元模型列表保存在 `second_fusion_model.pkl` 的
   `meta_feature_names`，预测端按此顺序收集概率，缺模型时降级。
