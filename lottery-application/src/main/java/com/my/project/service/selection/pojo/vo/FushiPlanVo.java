package com.my.project.service.selection.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 复式投注结果
 *
 * @author 刘强
 * @version 2025/11/07
 */
@Data
public class FushiPlanVo {

    private Long predictId;
    
    /**
     * 核心红球（原始推荐的6个）
     */
    private List<Integer> coreRedBalls;
    
    /**
     * 扩展后的所有红球（包含核心+扩展）
     */
    private List<Integer> allRedBalls;
    
    /**
     * 核心蓝球（原始推荐的1个）
     */
    private Integer coreBlueBall;
    
    /**
     * 扩展后的所有蓝球（包含核心+扩展）
     */
    private List<Integer> allBlueBalls;
    
    /**
     * 复式投注注数
     */
    private int notes;
    
    /**
     * 投注金额（元）
     */
    private int cost;
    
    /**
     * 开奖日期
     */
    private LocalDate openDate;
    
    /**
     * 核心号码综合分数
     */
    private BigDecimal coreScore;
    
    /**
     * 扩展说明
     */
    private String explanation;
    
    /**
     * 扩展详情
     */
    private ExpansionDetail detail;
    
    /**
     * 扩展详情类
     */
    @Data
    public static class ExpansionDetail {
        /**
         * 扩展的红球数量
         */
        private int expandedRedCount;
        
        /**
         * 扩展的蓝球数量
         */
        private int expandedBlueCount;
        
        /**
         * 扩展的红球列表
         */
        private List<Integer> expandedReds;
        
        /**
         * 扩展的蓝球列表
         */
        private List<Integer> expandedBlues;
        
        /**
         * 各扩展号码的得分
         */
        private String expansionScores;
        
        /**
         * 特征匹配说明
         */
        private String featureMatch;
    }
    
    // ==================== 构造方法 ====================
    
    public FushiPlanVo() {
    }
    
    public FushiPlanVo(List<Integer> coreReds, List<Integer> allReds,
                       Integer coreBlue, List<Integer> allBlues,
                       int notes, int cost, String explanation) {
        this.coreRedBalls = coreReds;
        this.allRedBalls = allReds;
        this.coreBlueBall = coreBlue;
        this.allBlueBalls = allBlues;
        this.notes = notes;
        this.cost = cost;
        this.explanation = explanation;
    }
    
    // ==================== 工具方法 ====================
    
    /**
     * 获取红球复式类型描述
     */
    public String getRedFushiType() {
        int redCount = allRedBalls.size();
        return redCount + "红复式";
    }
    
    /**
     * 获取蓝球复式类型描述
     */
    public String getBlueFushiType() {
        int blueCount = allBlueBalls.size();
        return blueCount + "蓝复式";
    }
    
    /**
     * 获取完整复式类型描述
     */
    public String getFullFushiType() {
        if (allBlueBalls.size() == 1) {
            return getRedFushiType();
        } else {
            return getRedFushiType() + " + " + getBlueFushiType();
        }
    }
    
    /**
     * 获取红球字符串（逗号分隔）
     */
    public String getRedBallsString() {
        return allRedBalls.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
    
    /**
     * 获取蓝球字符串（逗号分隔）
     */
    public String getBlueBallsString() {
        return allBlueBalls.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
    
    /**
     * 获取核心红球字符串
     */
    public String getCoreRedBallsString() {
        return coreRedBalls.stream()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
    
    /**
     * 计算单注成本
     */
    public BigDecimal getSingleNoteCost() {
        return BigDecimal.valueOf(2);
    }
    
    /**
     * 计算性价比（注数/金额）
     */
    public BigDecimal getCostEfficiency() {
        if (cost == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(notes)
                .divide(BigDecimal.valueOf(cost), 4, RoundingMode.HALF_UP);
    }
    
    /**
     * 获取预算使用率
     */
    public BigDecimal getBudgetUsageRate(int maxBudget) {
        if (maxBudget == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(cost)
                .divide(BigDecimal.valueOf(maxBudget), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
    
    /**
     * 生成简要说明
     */
    public String getBriefSummary() {
        return String.format("%s，%d注，%d元",
                getFullFushiType(), notes, cost);
    }
    
    /**
     * 生成详细说明
     */
    public String getDetailedSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("【复式方案】\n");
        sb.append("核心红球: ").append(getCoreRedBallsString()).append("\n");
        sb.append("复式红球: ").append(getRedBallsString()).append("\n");
        sb.append("核心蓝球: ").append(coreBlueBall).append("\n");
        sb.append("复式蓝球: ").append(getBlueBallsString()).append("\n");
        sb.append("投注信息: ").append(getFullFushiType())
          .append("，").append(notes).append("注，")
          .append(cost).append("元\n");
        if (explanation != null) {
            sb.append("扩展说明: ").append(explanation);
        }
        return sb.toString();
    }
    
    /**
     * 转换为 JSON 友好的 Map
     */
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("coreRedBalls", getCoreRedBallsString());
        map.put("allRedBalls", getRedBallsString());
        map.put("coreBlueBall", coreBlueBall);
        map.put("allBlueBalls", getBlueBallsString());
        map.put("fushiType", getFullFushiType());
        map.put("notes", notes);
        map.put("cost", cost);
        map.put("coreScore", coreScore);
        map.put("explanation", explanation);
        if (detail != null) {
            map.put("detail", detail);
        }
        return map;
    }
}

