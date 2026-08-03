package com.my.project.service.support;

import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.persistence.entity.PredictRecord;
import com.my.project.service.config.FushiExpandConfig;
import com.my.project.service.selection.pojo.bo.CandidateScoreBo;
import com.my.project.service.selection.pojo.bo.CoreFeaturesBo;
import com.my.project.service.selection.pojo.bo.ScoredCandidateBo;
import com.my.project.service.selection.pojo.vo.FushiPlanVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 复式扩展器
 * 将单式推荐号码智能扩展为复式投注方案
 * 基于历史中奖特征分析，选择与核心号码特征相似的号码进行扩展
 *
 * @author 刘强
 * @version 2025/11/07
 */
public class FushiExpanderUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(FushiExpanderUtils.class);
    
    /**
     * 将单式号码扩展为复式
     *
     * @param coreResult 核心推荐号码（单式）
     * @param history    历史开奖数据
     * @param config     扩展配置
     * @return 复式投注方案
     */
    public static FushiPlanVo expandToFushi(ScoredCandidateBo coreResult,
                                            List<HistoryRecord> history,
                                            FushiExpandConfig config) {
        logger.info("开始复式扩展，核心号码: {}, 预算: {}元",
                coreResult.getResult().getRedBalls(), config.getMaxBudget());
        
        // 1. 解析核心号码
        List<Integer> coreReds = parseRedBalls( coreResult.getResult().getRedBalls());
        int coreBlue =  coreResult.getResult().getBlueBall();
        
        // 2. 分析核心号码特征
        CoreFeaturesBo features = analyzeFeatures(coreReds, coreBlue, history);
        logger.info("核心特征: bigCount={}, oddCount={}, sum={}, zonePattern={}",
                features.bigCount, features.oddCount, features.sum, features.zonePattern);

        // 3. 找出候选扩展红球
        List<CandidateScoreBo> candidateReds = findCandidateReds(coreReds, features, history, config);
        logger.info("候选扩展红球: {}", candidateReds.stream()
                .limit(config.getMaxExpandReds())
                .map(c -> c.number + "(" + String.format("%.2f", c.score) + ")")
                .collect(Collectors.joining(", ")));
        
        // 4. 找出候选扩展蓝球
        List<CandidateScoreBo> candidateBlues = findCandidateBlues(coreBlue, features, history, config);
        if (!candidateBlues.isEmpty()) {
            logger.info("候选扩展蓝球: {}", candidateBlues.stream()
                    .limit(config.getMaxExpandBlues())
                    .map(c -> c.number + "(" + String.format("%.2f", c.score) + ")")
                    .collect(Collectors.joining(", ")));
        }
        
        // 5. 构建复式方案（考虑预算限制）
        FushiPlanVo result =
            buildFushiPlanVo(coreReds, candidateReds, coreBlue, candidateBlues,  coreResult.getResult(), features, config);
        logger.info("复式扩展完成: {}, {}注, {}元",
                result.getFullFushiType(), result.getNotes(), result.getCost());
        return result;
    }

    /**
     * 分析核心号码特征
     */
    private static CoreFeaturesBo analyzeFeatures(List<Integer> coreReds,
                                                 int coreBlue,
                                                 List<HistoryRecord> history) {
        CoreFeaturesBo features = new CoreFeaturesBo();
        
        // 数值特征
        features.bigCount = (int) coreReds.stream().filter(n -> n > 16).count();
        features.oddCount = (int) coreReds.stream().filter(n -> n % 2 == 1).count();
        features.sum = coreReds.stream().mapToInt(Integer::intValue).sum();
        
        // 区域特征
        int zone1 = (int) coreReds.stream().filter(n -> n <= 11).count();
        int zone2 = (int) coreReds.stream().filter(n -> n >= 12 && n <= 22).count();
        int zone3 = (int) coreReds.stream().filter(n -> n >= 23).count();
        features.zoneDistribution = new int[]{zone1, zone2, zone3};
        features.zonePattern = zone1 + "-" + zone2 + "-" + zone3;
        
        features.zones = new HashSet<>();
        for (int red : coreReds) {
            if (red <= 11) features.zones.add(1);
            else if (red <= 22) features.zones.add(2);
            else features.zones.add(3);
        }
        
        // 尾号特征
        features.tails = coreReds.stream()
                .map(n -> n % 10)
                .collect(Collectors.toSet());
        
        // 间隔特征
        List<Integer> sorted = new ArrayList<>(coreReds);
        Collections.sort(sorted);
        features.gaps = new ArrayList<>();
        for (int i = 0; i < sorted.size() - 1; i++) {
            features.gaps.add(sorted.get(i + 1) - sorted.get(i));
        }
        
        // 热度特征
        Map<Integer, Integer> hotness = calculateHotNumbers(history, 20);
        features.avgHotness = coreReds.stream()
                .mapToInt(n -> hotness.getOrDefault(n, 0))
                .average()
                .orElse(0.0);
        
        // 蓝球特征
        features.coreBlueIsOdd = (coreBlue % 2 == 1);
        features.coreBlueIsBig = (coreBlue > 8);
        features.coreBlueHotness = calculateBlueHotness(coreBlue, history, 20);
        
        return features;
    }
    
    // ==================== 红球扩展 ====================
    
    /**
     * 找出候选扩展红球
     */
    private static List<CandidateScoreBo> findCandidateReds(List<Integer> coreReds,
                                                           CoreFeaturesBo features,
                                                           List<HistoryRecord> history,
                                                           FushiExpandConfig config) {
        Map<Integer, Double> scores = new HashMap<>();
        Map<Integer, List<String>> reasons = new HashMap<>();
        
        // 遍历所有红球，为未选中的打分
        for (int ball = 1; ball <= 33; ball++) {
            if (coreReds.contains(ball)) continue;
            
            double totalScore = 0.0;
            List<String> scoreReasons = new ArrayList<>();
            
            // 策略1: 同区扩展
            double zoneScore = calculateZoneScore(ball, features.zones) * config.getSameZoneWeight();
            if (zoneScore > 0) {
                totalScore += zoneScore;
                scoreReasons.add(String.format("同区%.2f", zoneScore));
            }
            
            // 策略2: 相邻号码
            double adjacentScore = calculateAdjacentScore(ball, coreReds) * config.getAdjacentWeight();
            if (adjacentScore > 0) {
                totalScore += adjacentScore;
                scoreReasons.add(String.format("相邻%.2f", adjacentScore));
            }
            
            // 策略3: 同尾号码
            double tailScore = calculateTailScore(ball, features.tails) * config.getSameTailWeight();
            if (tailScore > 0) {
                totalScore += tailScore;
                scoreReasons.add(String.format("同尾%.2f", tailScore));
            }
            
            // 策略4: 奇偶均衡
            double oddEvenScore = calculateOddEvenBalanceScore(ball, coreReds, features.oddCount)
                    * config.getOddEvenBalanceWeight();
            if (oddEvenScore > 0) {
                totalScore += oddEvenScore;
                scoreReasons.add(String.format("奇偶%.2f", oddEvenScore));
            }
            
            // 策略5: 大小均衡
            double bigSmallScore = calculateBigSmallBalanceScore(ball, coreReds, features.bigCount)
                    * config.getBigSmallBalanceWeight();
            if (bigSmallScore > 0) {
                totalScore += bigSmallScore;
                scoreReasons.add(String.format("大小%.2f", bigSmallScore));
            }
            
            // 策略6: 热号优先
            Map<Integer, Integer> hotness = calculateHotNumbers(history, config.getHotnessWindowSize());
            double hotnessScore = (hotness.getOrDefault(ball, 0) / (double) config.getHotnessWindowSize())
                    * config.getHotnessWeight();
            if (hotnessScore > 0) {
                totalScore += hotnessScore;
                scoreReasons.add(String.format("热号%.2f", hotnessScore));
            }
            
            // 策略7: 共现分析
            double cooccurrenceScore = calculateCooccurrence(ball, coreReds, history, config.getCooccurrenceWindowSize())
                    * config.getCooccurrenceWeight();
            if (cooccurrenceScore > 0) {
                totalScore += cooccurrenceScore;
                scoreReasons.add(String.format("共现%.2f", cooccurrenceScore));
            }
            
            scores.put(ball, totalScore);
            reasons.put(ball, scoreReasons);
        }
        
        // 按分数排序，返回候选列表
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .map(e -> new CandidateScoreBo(e.getKey(), e.getValue(),
                        String.join("+", reasons.get(e.getKey()))))
                .collect(Collectors.toList());
    }
    
    /**
     * 计算同区得分
     */
    private static double calculateZoneScore(int ball, Set<Integer> coreZones) {
        int ballZone = (ball <= 11) ? 1 : (ball <= 22) ? 2 : 3;
        return coreZones.contains(ballZone) ? 1.0 : 0.3;
    }
    
    /**
     * 计算相邻得分
     */
    private static double calculateAdjacentScore(int ball, List<Integer> coreReds) {
        for (int core : coreReds) {
            if (Math.abs(ball - core) == 1) {
                return 1.0;  // 直接相邻
            } else if (Math.abs(ball - core) == 2) {
                return 0.5;  // 间隔1个
            }
        }
        return 0.0;
    }
    
    /**
     * 计算同尾得分
     */
    private static double calculateTailScore(int ball, Set<Integer> coreTails) {
        int ballTail = ball % 10;
        return coreTails.contains(ballTail) ? 1.0 : 0.0;
    }
    
    /**
     * 计算奇偶均衡得分
     */
    private static double calculateOddEvenBalanceScore(int ball, List<Integer> coreReds, int coreOddCount) {
        boolean ballIsOdd = (ball % 2 == 1);
        int newOddCount = coreOddCount + (ballIsOdd ? 1 : 0);
        int newEvenCount = coreReds.size() + 1 - newOddCount;
        
        // 理想奇偶比例：3-4奇
        if (newOddCount >= 3 && newOddCount <= 4) {
            return 1.0;
        } else if (newOddCount >= 2 && newOddCount <= 5) {
            return 0.5;
        }
        return 0.0;
    }
    
    /**
     * 计算大小均衡得分
     */
    private static double calculateBigSmallBalanceScore(int ball, List<Integer> coreReds, int coreBigCount) {
        boolean ballIsBig = (ball > 16);
        int newBigCount = coreBigCount + (ballIsBig ? 1 : 0);
        int newSmallCount = coreReds.size() + 1 - newBigCount;
        
        // 理想大小比例：2-3大
        if (newBigCount >= 2 && newBigCount <= 4) {
            return 1.0;
        } else if (newBigCount >= 1 && newBigCount <= 5) {
            return 0.5;
        }
        return 0.0;
    }
    
    /**
     * 计算共现得分
     */
    private static double calculateCooccurrence(int candidate, List<Integer> coreReds,
                                                 List<HistoryRecord> history, int windowSize) {
        int cooccurCount = 0;
        int totalCount = 0;
        
        int start = Math.max(0, history.size() - windowSize);
        for (int i = start; i < history.size(); i++) {
            List<Integer> historyReds = getRedBalls(history.get(i));
            
            if (historyReds.contains(candidate)) {
                totalCount++;
                // 检查是否与核心号码共现
                long matchCount = coreReds.stream().filter(historyReds::contains).count();
                if (matchCount >= 2) {  // 至少与2个核心号码共现
                    cooccurCount++;
                }
            }
        }
        
        return totalCount == 0 ? 0.0 : (cooccurCount / (double) totalCount);
    }
    
    // ==================== 蓝球扩展 ====================
    
    /**
     * 找出候选扩展蓝球
     */
    private static List<CandidateScoreBo> findCandidateBlues(int coreBlue,
                                                            CoreFeaturesBo features,
                                                            List<HistoryRecord> history,
                                                            FushiExpandConfig config) {
        Map<Integer, Double> scores = new HashMap<>();
        Map<Integer, List<String>> reasons = new HashMap<>();
        
        for (int blue = 1; blue <= 16; blue++) {
            if (blue == coreBlue) continue;
            
            double totalScore = 0.0;
            List<String> scoreReasons = new ArrayList<>();
            
            // 策略1: 奇偶一致性
            boolean blueIsOdd = (blue % 2 == 1);
            if (blueIsOdd == features.coreBlueIsOdd) {
                double score = config.getBlueOddEvenWeight();
                totalScore += score;
                scoreReasons.add(String.format("奇偶%.2f", score));
            }
            
            // 策略2: 大小一致性
            boolean blueIsBig = (blue > 8);
            if (blueIsBig == features.coreBlueIsBig) {
                double score = config.getBlueBigSmallWeight();
                totalScore += score;
                scoreReasons.add(String.format("大小%.2f", score));
            }
            
            // 策略3: 热度相近
            int blueHotness = calculateBlueHotness(blue, history, config.getHotnessWindowSize());
            if (Math.abs(blueHotness - features.coreBlueHotness) <= 2) {
                double score = config.getBlueHotnessSimilarWeight();
                totalScore += score;
                scoreReasons.add(String.format("热度相近%.2f", score));
            }
            
            // 策略4: 整体热度
            double hotnessScore = (blueHotness / (double) config.getHotnessWindowSize())
                    * config.getBlueOverallHotnessWeight();
            if (hotnessScore > 0) {
                totalScore += hotnessScore;
                scoreReasons.add(String.format("热度%.2f", hotnessScore));
            }
            
            scores.put(blue, totalScore);
            reasons.put(blue, scoreReasons);
        }
        
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .map(e -> new CandidateScoreBo(e.getKey(), e.getValue(),
                        String.join("+", reasons.get(e.getKey()))))
                .collect(Collectors.toList());
    }
    
    // ==================== 构建复式方案 ====================
    
    /**
     * 构建复式方案（考虑预算限制）
     */
    private static FushiPlanVo buildFushiPlanVo(List<Integer> coreReds,
                                                 List<CandidateScoreBo> candidateReds,
                                                 int coreBlue,
                                                 List<CandidateScoreBo> candidateBlues,
                                                 PredictRecord coreResult,
                                                 CoreFeaturesBo features,
                                                 FushiExpandConfig config) {
        // 合并核心号码和候选号码
        List<Integer> allReds = new ArrayList<>(coreReds);
        List<Integer> expandedReds = new ArrayList<>();
        
        // 添加候选红球
        for (CandidateScoreBo candidate : candidateReds) {
            if (expandedReds.size() >= config.getMaxExpandReds()) break;
            allReds.add(candidate.number);
            expandedReds.add(candidate.number);
        }
        
        List<Integer> allBlues = new ArrayList<>();
        allBlues.add(coreBlue);
        List<Integer> expandedBlues = new ArrayList<>();
        
        // 添加候选蓝球
        for (CandidateScoreBo candidate : candidateBlues) {
            if (expandedBlues.size() >= config.getMaxExpandBlues()) break;
            allBlues.add(candidate.number);
            expandedBlues.add(candidate.number);
        }
        
        // 根据预算自动调整（从大到小尝试）
        Collections.sort(allReds);
        Collections.sort(allBlues);
        
        for (int r = allReds.size(); r >= 7; r--) {
            for (int b = allBlues.size(); b >= 1; b--) {
                int notes = combination(r, 6) * b;
                int cost = notes * 2;
                
                if (cost >= config.getMinBudget() && cost <= config.getMaxBudget()) {
                    // 找到合适的方案
                    List<Integer> finalReds = allReds.subList(0, r);
                    List<Integer> finalBlues = allBlues.subList(0, b);
                    
                    FushiPlanVo result = new FushiPlanVo();
                    result.setCoreRedBalls(new ArrayList<>(coreReds));
                    result.setAllRedBalls(finalReds);
                    result.setCoreBlueBall(coreBlue);
                    result.setAllBlueBalls(finalBlues);
                    result.setNotes(notes);
                    result.setCost(cost);
                    result.setOpenDate(coreResult.getOpenDate());
                    result.setCoreScore(coreResult.getTotalScore());
                    result.setPredictId(coreResult.getId());
                    result.setExplanation(generateExplanation(
                            coreReds, finalReds, coreBlue, finalBlues,
                            features, candidateReds, candidateBlues));
                    
                    // 设置扩展详情
                    FushiPlanVo.ExpansionDetail detail = new FushiPlanVo.ExpansionDetail();
                    detail.setExpandedRedCount(finalReds.size() - coreReds.size());
                    detail.setExpandedBlueCount(finalBlues.size() - 1);
                    detail.setExpandedReds(finalReds.stream()
                            .filter(n -> !coreReds.contains(n))
                            .collect(Collectors.toList()));
                    detail.setExpandedBlues(finalBlues.stream()
                            .filter(n -> n != coreBlue)
                            .collect(Collectors.toList()));
                    
                    // 扩展号码得分说明
                    StringBuilder scoreInfo = new StringBuilder();
                    for (int red : detail.getExpandedReds()) {
                        candidateReds.stream()
                                .filter(c -> c.number == red)
                                .findFirst()
                                .ifPresent(c -> scoreInfo.append(String.format("%d(%.2f:%s) ",
                                        c.number, c.score, c.reason)));
                    }
                    detail.setExpansionScores(scoreInfo.toString().trim());
                    
                    // 特征匹配说明
                    detail.setFeatureMatch(String.format(
                            "核心特征: %d大%d小, %d奇%d偶, 总和%d, 区域%s",
                            features.bigCount, 6 - features.bigCount,
                            features.oddCount, 6 - features.oddCount,
                            features.sum, features.zonePattern));
                    
                    result.setDetail(detail);
                    
                    return result;
                }
            }
        }
        
        // 如果都不满足，返回最小复式（7红1蓝）
        List<Integer> minReds = allReds.subList(0, Math.min(7, allReds.size()));
        List<Integer> minBlues = List.of(coreBlue);
        int minNotes = combination(minReds.size(), 6);
        int minCost = minNotes * 2;
        
        FushiPlanVo result = new FushiPlanVo();
        result.setCoreRedBalls(new ArrayList<>(coreReds));
        result.setAllRedBalls(minReds);
        result.setCoreBlueBall(coreBlue);
        result.setAllBlueBalls(minBlues);
        result.setNotes(minNotes);
        result.setCost(minCost);
        result.setPredictId(coreResult.getId());
        result.setOpenDate(coreResult.getOpenDate());
        result.setCoreScore(coreResult.getTotalScore());
        result.setExplanation("预算限制，返回最小复式方案（7红1蓝）");
        
        return result;
    }
    
    /**
     * 生成扩展说明
     */
    private static String generateExplanation(List<Integer> coreReds, List<Integer> finalReds,
                                               int coreBlue, List<Integer> finalBlues,
                                               CoreFeaturesBo features,
                                               List<CandidateScoreBo> candidateReds,
                                               List<CandidateScoreBo> candidateBlues) {
        StringBuilder sb = new StringBuilder();
        
        // 核心特征
        sb.append(String.format("基于核心号码特征（%d大%d小，%d奇%d偶，总和%d，区域%s），",
                features.bigCount, 6 - features.bigCount,
                features.oddCount, 6 - features.oddCount,
                features.sum, features.zonePattern));
        
        // 红球扩展
        int expandedRedCount = finalReds.size() - coreReds.size();
        if (expandedRedCount > 0) {
            List<Integer> expanded = finalReds.stream()
                    .filter(n -> !coreReds.contains(n))
                    .collect(Collectors.toList());
            sb.append(String.format("扩展了%d个红球(%s)", expandedRedCount,
                    expanded.stream().map(String::valueOf).collect(Collectors.joining(","))));
            
            // 添加扩展原因
            List<String> reasons = new ArrayList<>();
            for (int red : expanded) {
                candidateReds.stream()
                        .filter(c -> c.number == red)
                        .findFirst()
                        .ifPresent(c -> reasons.add(c.reason));
            }
            if (!reasons.isEmpty()) {
                sb.append("，原因: ").append(String.join("；", reasons));
            }
        }
        
        // 蓝球扩展
        int expandedBlueCount = finalBlues.size() - 1;
        if (expandedBlueCount > 0) {
            List<Integer> expanded = finalBlues.stream()
                    .filter(n -> n != coreBlue)
                    .collect(Collectors.toList());
            sb.append(String.format("；扩展了%d个蓝球(%s)", expandedBlueCount,
                    expanded.stream().map(String::valueOf).collect(Collectors.joining(","))));
        }
        
        return sb.toString();
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 解析红球字符串
     */
    private static List<Integer> parseRedBalls(String redBalls) {
        if (redBalls == null || redBalls.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(redBalls.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }
    
    /**
     * 从HistoryRecord提取红球
     */
    private static List<Integer> getRedBalls(HistoryRecord record) {
        return Arrays.asList(
                record.getNum1(), record.getNum2(), record.getNum3(),
                record.getNum4(), record.getNum5(), record.getNum6()
        );
    }
    
    /**
     * 计算组合数 C(n, r)
     */
    private static int combination(int n, int r) {
        if (r > n) return 0;
        if (r == 0 || r == n) return 1;
        
        // 使用公式 C(n,r) = n! / (r! * (n-r)!)
        // 优化: C(n,r) = C(n, n-r)，选择较小的r
        r = Math.min(r, n - r);
        
        long result = 1;
        for (int i = 0; i < r; i++) {
            result = result * (n - i) / (i + 1);
        }
        
        return (int) result;
    }
    
    /**
     * 统计热号
     */
    private static Map<Integer, Integer> calculateHotNumbers(List<HistoryRecord> history, int windowSize) {
        Map<Integer, Integer> hotNumbers = new HashMap<>();
        
        int start = Math.max(0, history.size() - windowSize);
        for (int i = start; i < history.size(); i++) {
            HistoryRecord record = history.get(i);
            hotNumbers.merge(record.getNum1(), 1, Integer::sum);
            hotNumbers.merge(record.getNum2(), 1, Integer::sum);
            hotNumbers.merge(record.getNum3(), 1, Integer::sum);
            hotNumbers.merge(record.getNum4(), 1, Integer::sum);
            hotNumbers.merge(record.getNum5(), 1, Integer::sum);
            hotNumbers.merge(record.getNum6(), 1, Integer::sum);
        }
        
        return hotNumbers;
    }
    
    /**
     * 计算蓝球热度
     */
    private static int calculateBlueHotness(int blueBall, List<HistoryRecord> history, int windowSize) {
        int hotness = 0;
        int start = Math.max(0, history.size() - windowSize);
        
        for (int i = start; i < history.size(); i++) {
            if (history.get(i).getSpecial() == blueBall) {
                hotness++;
            }
        }
        
        return hotness;
    }
}

