package com.my.project.service.config;

import lombok.Data;

/**
 * 复式扩展配置
 * 用于配置如何将单式号码扩展为复式投注方案
 *
 * @author 刘强
 * @version 2025/11/07
 */
@Data
public class FushiExpandConfig {
    
    // ==================== 扩展数量配置 ====================
    
    /**
     * 最少扩展红球数量（在核心6个基础上增加）
     * 默认1个，即最少7红复式
     */
    private int minExpandReds = 1;
    
    /**
     * 最多扩展红球数量（在核心6个基础上增加）
     * 默认4个，即最多10红复式
     */
    private int maxExpandReds = 4;
    
    /**
     * 最少扩展蓝球数量
     * 默认0个，即保持1蓝
     */
    private int minExpandBlues = 0;
    
    /**
     * 最多扩展蓝球数量
     * 默认2个，即最多3蓝
     */
    private int maxExpandBlues = 2;
    
    // ==================== 预算控制 ====================
    
    /**
     * 最大投注金额（元）
     * 默认200元
     */
    private int maxBudget = 200;
    
    /**
     * 最小投注金额（元）
     * 默认14元（7红1蓝）
     */
    private int minBudget = 14;
    
    // ==================== 红球扩展策略权重 ====================
    
    /**
     * 同区扩展权重
     * 优先选择与核心号码同区域的号码，保持区域均衡
     */
    private double sameZoneWeight = 0.30;
    
    /**
     * 相邻号码权重
     * 优先选择与核心号码相邻的号码，形成连号
     */
    private double adjacentWeight = 0.25;
    
    /**
     * 同尾号码权重
     * 优先选择与核心号码尾号相同的号码
     */
    private double sameTailWeight = 0.20;
    
    /**
     * 奇偶均衡权重
     * 保持奇偶比例均衡
     */
    private double oddEvenBalanceWeight = 0.20;
    
    /**
     * 大小均衡权重
     * 保持大小号比例均衡
     */
    private double bigSmallBalanceWeight = 0.20;
    
    /**
     * 热号权重
     * 优先选择最近期数的热门号码
     */
    private double hotnessWeight = 0.15;
    
    /**
     * 共现权重
     * 优先选择历史上与核心号码经常共同出现的号码
     */
    private double cooccurrenceWeight = 0.20;
    
    // ==================== 蓝球扩展策略权重 ====================
    
    /**
     * 蓝球奇偶一致性权重
     */
    private double blueOddEvenWeight = 0.30;
    
    /**
     * 蓝球热度相近权重
     */
    private double blueHotnessSimilarWeight = 0.30;
    
    /**
     * 蓝球大小一致性权重
     */
    private double blueBigSmallWeight = 0.20;
    
    /**
     * 蓝球整体热度权重
     */
    private double blueOverallHotnessWeight = 0.20;
    
    // ==================== 其他配置 ====================
    
    /**
     * 热号统计期数
     * 统计最近多少期的数据来判断热号
     */
    private int hotnessWindowSize = 20;
    
    /**
     * 共现统计期数
     * 统计最近多少期的数据来判断号码共现规律
     */
    private int cooccurrenceWindowSize = 30;
    
    /**
     * 是否优先保证核心号码全部包含
     * true: 扩展号码一定包含所有核心号码
     * false: 可能替换部分核心号码
     */
    private boolean guaranteeCoreNumbers = true;
    
    /**
     * 扩展模式
     * CONSERVATIVE: 保守型，只扩展1-2个号码
     * BALANCED: 均衡型，扩展2-3个号码
     * AGGRESSIVE: 激进型，扩展3-4个号码
     * AUTO: 自动根据预算决定
     */
    private ExpansionMode expansionMode = ExpansionMode.AUTO;
    
    /**
     * 扩展模式枚举
     */
    public enum ExpansionMode {
        CONSERVATIVE,  // 保守型
        BALANCED,      // 均衡型
        AGGRESSIVE,    // 激进型
        AUTO           // 自动
    }
    
    // ==================== 预定义配置 ====================
    
    /**
     * 获取保守型配置（预算50-100元）
     */
    public static FushiExpandConfig conservative() {
        FushiExpandConfig config = new FushiExpandConfig();
        config.setMaxExpandReds(2);      // 最多8红
        config.setMaxExpandBlues(1);     // 最多2蓝
        config.setMaxBudget(100);
        config.setExpansionMode(ExpansionMode.CONSERVATIVE);
        return config;
    }
    
    /**
     * 获取均衡型配置（预算100-300元）
     */
    public static FushiExpandConfig balanced() {
        FushiExpandConfig config = new FushiExpandConfig();
        config.setMaxExpandReds(3);      // 最多9红
        config.setMaxExpandBlues(2);     // 最多3蓝
        config.setMaxBudget(300);
        config.setExpansionMode(ExpansionMode.BALANCED);
        return config;
    }
    
    /**
     * 获取激进型配置（预算300-1000元）
     */
    public static FushiExpandConfig aggressive() {
        FushiExpandConfig config = new FushiExpandConfig();
        config.setMaxExpandReds(4);      // 最多10红
        config.setMaxExpandBlues(3);     // 最多4蓝
        config.setMaxBudget(1000);
        config.setExpansionMode(ExpansionMode.AGGRESSIVE);
        return config;
    }
    
    /**
     * 根据预算自动生成配置
     */
    public static FushiExpandConfig auto(int budget) {
        if (budget < 100) {
            return conservative();
        } else if (budget < 300) {
            return balanced();
        } else {
            return aggressive();
        }
    }
}

