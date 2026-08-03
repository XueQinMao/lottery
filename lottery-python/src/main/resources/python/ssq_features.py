#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
双色球特征与标签计算模块（训练 / 预测 / 回测共用）

设计要点：
1. 全部特征基于原始开奖号码计算，不依赖 Java 端预生成的 CSV，
   使 Python 侧 ML 流水线自包含、可独立迭代。
2. 既保留原有 13 个统计特征（向后兼容），又补充 AC 值、遗漏值、
   重号、蓝球 012 路、红球三区分布等 9 个新特征。
3. 标签：辅目标为形态「典型性」；主监督目标 PRIMARY_LABEL=is_positive
   （真实开奖 vs 随机票），由训练脚本写入，避免与特征确定性可推。
4. 遗漏值是动态特征：训练样本 i 用 history[:i] 计算，预测时用全量历史。
"""

from typing import List, Tuple, Dict, Optional

# ============================== 常量 ==============================
RED_MAX = 33
BLUE_MAX = 16
RED_COUNT = 6
LOOKBACK = 5  # LSTM / Transformer 回看期数

# 质数集合（用于 hot_hits，沿用旧定义以保持向后兼容）
PRIMES = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31}

# 特征列（顺序固定，训练 / 预测必须一致）
FEATURE_COLUMNS = [
    # ---- 原 13 维统计特征 ----
    'sum_red', 'span_red', 'odd_count', 'even_count',
    'big_count', 'small_count', 'hot_hits', 'cold_hits',
    'blue_hot', 'red_sum_last_diff', 'red_max_last_diff',
    'consecutive_count', 'same_tail_count',
    # ---- 新增 9 维特征 ----
    'ac_value',            # AC 值（算术复杂度）
    'repeat_from_last',    # 与上一期重号个数
    'max_missing',         # 6 个红球中最大遗漏期数
    'avg_missing',         # 6 个红球平均遗漏期数
    'blue_missing',        # 蓝球遗漏期数
    'blue_012_road',       # 蓝球 012 路（0/1/2）
    'zone1_count',         # 一区（1-11）个数
    'zone2_count',         # 二区（12-22）个数
    'zone3_count',         # 三区（23-33）个数
]

# 辅助多目标标签（号码形态「典型性」，仅作辅模型；不可作主监督目标，
# 因其几乎可由 FEATURE_COLUMNS 中同名字段确定性推出）
LABEL_COLUMNS = [
    'label_sum',         # 红球和值是否落在典型区间
    'label_span',        # 红球跨度是否落在典型区间
    'label_odd_even',    # 奇偶比是否典型
    'label_zone',        # 三区分布是否均衡
    'label_blue_odd',    # 蓝球是否为奇数
    'label_blue_big',    # 蓝球是否为大号（>=9）
]
# 主监督目标：真实开奖(正) vs 随机票(负)，由 build_dataset 写入 is_positive
PRIMARY_LABEL = 'is_positive'

# 典型区间（基于历史统计经验值）
SUM_TYPICAL = (90, 120)
SPAN_TYPICAL = (16, 28)
ODD_TYPICAL = {2, 3, 4}


# ============================== 基础统计 ==============================
def compute_zones(red_balls: List[int]) -> Tuple[int, int, int]:
    """红球三区分布：zone1=1-11, zone2=12-22, zone3=23-33"""
    z1 = sum(1 for r in red_balls if 1 <= r <= 11)
    z2 = sum(1 for r in red_balls if 12 <= r <= 22)
    z3 = sum(1 for r in red_balls if 23 <= r <= 33)
    return z1, z2, z3


def compute_ac_value(red_balls: List[int]) -> int:
    """AC 值 = 不同差值个数 - (n-1)，n=6 时减 5"""
    diffs = set()
    sorted_balls = sorted(red_balls)
    for i in range(len(sorted_balls)):
        for j in range(i + 1, len(sorted_balls)):
            diffs.add(abs(sorted_balls[j] - sorted_balls[i]))
    return max(len(diffs) - (RED_COUNT - 1), 0)


def count_consecutive(red_balls: List[int]) -> int:
    """连号对数"""
    sorted_balls = sorted(red_balls)
    return sum(1 for i in range(len(sorted_balls) - 1)
               if sorted_balls[i + 1] - sorted_balls[i] == 1)


def count_same_tail(red_balls: List[int]) -> int:
    """同尾号个数（同尾多余计数）"""
    tail_count: Dict[int, int] = {}
    for b in red_balls:
        tail = b % 10
        tail_count[tail] = tail_count.get(tail, 0) + 1
    return sum(c - 1 for c in tail_count.values() if c > 1)


def count_repeat_from_last(red_balls: List[int],
                           prev_red_balls: Optional[List[int]]) -> int:
    """与上一期重复的红球个数"""
    if not prev_red_balls:
        return 0
    prev_set = set(prev_red_balls)
    return sum(1 for r in red_balls if r in prev_set)


def blue_road(blue: int) -> int:
    """蓝球 012 路：blue % 3"""
    return blue % 3


# ============================== 遗漏值 ==============================
def compute_red_missing(history_reds: List[List[int]]) -> Dict[int, int]:
    """
    给定历史红球序列（oldest -> newest），返回每个红球(1-33)的遗漏期数。
    遗漏 = 距离最近一次出现的期数；从未出现 = len(history)。
    """
    n = len(history_reds)
    missing = {b: n for b in range(1, RED_MAX + 1)}
    # 从最新一期往回找
    for idx in range(n - 1, -1, -1):
        for b in history_reds[idx]:
            if missing[b] == n:  # 尚未命中过
                missing[b] = n - 1 - idx
    return missing


def compute_blue_missing(history_blues: List[int]) -> Dict[int, int]:
    """蓝球遗漏期数字典"""
    n = len(history_blues)
    missing = {b: n for b in range(1, BLUE_MAX + 1)}
    for idx in range(n - 1, -1, -1):
        b = history_blues[idx]
        if missing[b] == n:
            missing[b] = n - 1 - idx
    return missing


# ============================== 完整特征向量 ==============================
def compute_features(red_balls: List[int],
                     blue_ball: int,
                     prev_red_balls: Optional[List[int]] = None,
                     prev_blue_ball: Optional[int] = None,
                     red_missing: Optional[Dict[int, int]] = None,
                     blue_missing_map: Optional[Dict[int, int]] = None) -> Dict[str, float]:
    """
    计算单组号码的完整 22 维特征。

    red_missing / blue_missing_map：当前历史状态下的遗漏字典。
    训练样本应传入"该期之前"的遗漏；预测时传入"全量历史"的遗漏。
    """
    red_balls = sorted(red_balls)
    sum_red = sum(red_balls)
    span_red = red_balls[-1] - red_balls[0]
    odd_count = sum(1 for r in red_balls if r % 2 == 1)
    big_count = sum(1 for r in red_balls if r >= 17)
    hot_hits = sum(1 for r in red_balls if r in PRIMES)
    z1, z2, z3 = compute_zones(red_balls)

    prev_sum = sum(prev_red_balls) if prev_red_balls else sum_red
    prev_max = max(prev_red_balls) if prev_red_balls else max(red_balls)

    # 遗漏特征
    if red_missing is None:
        red_missing = {b: 0 for b in range(1, RED_MAX + 1)}
    if blue_missing_map is None:
        blue_missing_map = {b: 0 for b in range(1, BLUE_MAX + 1)}
    sel_missing = [red_missing[b] for b in red_balls]
    max_missing = max(sel_missing) if sel_missing else 0
    avg_missing = sum(sel_missing) / len(sel_missing) if sel_missing else 0.0
    blue_miss = blue_missing_map.get(blue_ball, 0)

    features = {
        'sum_red': sum_red,
        'span_red': span_red,
        'odd_count': odd_count,
        'even_count': RED_COUNT - odd_count,
        'big_count': big_count,
        'small_count': RED_COUNT - big_count,
        'hot_hits': hot_hits,
        'cold_hits': RED_COUNT - hot_hits,
        'blue_hot': 3 if blue_ball % 2 == 0 else 2,  # 沿用旧定义
        'red_sum_last_diff': sum_red - prev_sum,
        'red_max_last_diff': max(red_balls) - prev_max,
        'consecutive_count': count_consecutive(red_balls),
        'same_tail_count': count_same_tail(red_balls),
        'ac_value': compute_ac_value(red_balls),
        'repeat_from_last': count_repeat_from_last(red_balls, prev_red_balls),
        'max_missing': max_missing,
        'avg_missing': avg_missing,
        'blue_missing': blue_miss,
        'blue_012_road': blue_road(blue_ball),
        'zone1_count': z1,
        'zone2_count': z2,
        'zone3_count': z3,
    }
    return features


def feature_vector(red_balls: List[int],
                   blue_ball: int,
                   prev_red_balls: Optional[List[int]] = None,
                   prev_blue_ball: Optional[int] = None,
                   red_missing: Optional[Dict[int, int]] = None,
                   blue_missing_map: Optional[Dict[int, int]] = None) -> List[float]:
    """按 FEATURE_COLUMNS 顺序返回特征向量"""
    feats = compute_features(red_balls, blue_ball, prev_red_balls,
                             prev_blue_ball, red_missing, blue_missing_map)
    return [feats[c] for c in FEATURE_COLUMNS]


# ============================== 多目标标签 ==============================
def compute_labels(red_balls: List[int], blue_ball: int) -> Dict[str, int]:
    """计算 6 个多目标标签"""
    red_balls = sorted(red_balls)
    sum_red = sum(red_balls)
    span_red = red_balls[-1] - red_balls[0]
    odd_count = sum(1 for r in red_balls if r % 2 == 1)
    z1, z2, z3 = compute_zones(red_balls)

    return {
        'label_sum': 1 if SUM_TYPICAL[0] <= sum_red <= SUM_TYPICAL[1] else 0,
        'label_span': 1 if SPAN_TYPICAL[0] <= span_red <= SPAN_TYPICAL[1] else 0,
        'label_odd_even': 1 if odd_count in ODD_TYPICAL else 0,
        'label_zone': 1 if all(1 <= z <= 4 for z in (z1, z2, z3)) else 0,
        'label_blue_odd': 1 if blue_ball % 2 == 1 else 0,
        'label_blue_big': 1 if blue_ball >= 9 else 0,
    }


# ============================== 序列特征（LSTM / Transformer） ==============================
def sequence_vector(red_balls: List[int],
                    blue_ball: int,
                    history_last_n: List[Tuple[List[int], int]]) -> List[int]:
    """
    构造序列特征向量：[当前期 7 个号码, 历史 t1 7 个, ..., 历史 tn 7 个]
    history_last_n: 最近 n 期（oldest -> newest），每期 (red_balls, blue)
    长度 = 7 * (n + 1)
    """
    vec: List[int] = []
    vec.extend(sorted(red_balls))
    vec.append(blue_ball)
    for reds, blue in history_last_n:
        vec.extend(sorted(reds))
        vec.append(blue)
    return vec


def sequence_columns(lookback: int = LOOKBACK) -> List[str]:
    """序列特征列名（与 Java 端 generateSequenceFeatures 一致）"""
    cols = []
    for i in range(1, 7):
        cols.append(f'red_{i}')
    cols.append('blue')
    for t in range(1, lookback + 1):
        for i in range(1, 7):
            cols.append(f'red_{i}_t{t}')
        cols.append(f'blue_t{t}')
    return cols


# ============================== Markov 状态 ==============================
def markov_state_id(red_balls: List[int]) -> int:
    """红球三区分布状态编码：zone1*100 + zone2*10 + zone3"""
    z1, z2, z3 = compute_zones(red_balls)
    return z1 * 100 + z2 * 10 + z3


def markov_transition_type(state_id: int, prev_state_id: int) -> str:
    diff = abs(state_id - prev_state_id)
    if diff == 0:
        return 'SAME'
    elif diff < 50:
        return 'STABLE'
    elif diff < 150:
        return 'MODERATE'
    return 'DRASTIC'


def blue_state(blue_ball: int) -> str:
    if blue_ball % 2 == 1:
        return 'ODD_SMALL' if blue_ball <= 8 else 'ODD_BIG'
    return 'EVEN_SMALL' if blue_ball <= 8 else 'EVEN_BIG'


# ============================== 候选号码生成 ==============================
def random_combination(rng) -> Tuple[List[int], int]:
    """随机生成一组号码（用于负样本 / 候选）"""
    red_set = set()
    while len(red_set) < RED_COUNT:
        red_set.add(rng.randint(1, RED_MAX))
    blue = rng.randint(1, BLUE_MAX)
    return sorted(red_set), blue


def smart_sample_combinations(history_reds: List[List[int]],
                              history_blues: List[int],
                              n: int,
                              rng,
                              hot_weight: float = 0.6) -> List[Tuple[List[int], int]]:
    """
    智能采样候选组合：
    - hot_weight 比例按历史频率加权抽红球（偏热号）
    - 其余完全随机
    每组返回 (red_balls, blue_ball)
    """
    # 计算每个红球历史出现次数
    freq = {b: 0 for b in range(1, RED_MAX + 1)}
    for reds in history_reds:
        for b in reds:
            freq[b] += 1
    balls = list(range(1, RED_MAX + 1))
    weights = [freq[b] + 1 for b in balls]  # +1 平滑

    blue_freq = {b: 0 for b in range(1, BLUE_MAX + 1)}
    for b in history_blues:
        blue_freq[b] += 1
    blue_balls = list(range(1, BLUE_MAX + 1))
    blue_weights = [blue_freq[b] + 1 for b in blue_balls]

    candidates: List[Tuple[List[int], int]] = []
    for _ in range(n):
        if rng.random() < hot_weight:
            # 加权抽 6 个不重复红球
            chosen = set()
            local_w = list(weights)
            while len(chosen) < RED_COUNT:
                r = rng.choices(balls, weights=local_w, k=1)[0]
                if r not in chosen:
                    chosen.add(r)
                    # 置零避免重复
                    local_w[balls.index(r)] = 0
            reds = sorted(chosen)
        else:
            red_set = set()
            while len(red_set) < RED_COUNT:
                red_set.add(rng.randint(1, RED_MAX))
            reds = sorted(red_set)
        blue = rng.choices(blue_balls, weights=blue_weights, k=1)[0] \
            if rng.random() < hot_weight else rng.randint(1, BLUE_MAX)
        candidates.append((reds, blue))
    return candidates
