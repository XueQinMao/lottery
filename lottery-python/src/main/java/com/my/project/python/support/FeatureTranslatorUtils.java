package com.my.project.python.support;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 特征名称中文转义工具类
 *
 * @author 刘强
 * @version 2025/10/29 09:00
 **/
public class FeatureTranslatorUtils {

    /**
     * 特征名称中英文对照表
     */
    private static final Map<String, String> FEATURE_TRANSLATION = new HashMap<>();
    
    /**
     * 模型名称中英文对照表
     */
    private static final Map<String, String> MODEL_TRANSLATION = new HashMap<>();

    static {
        // 特征名称翻译
        FEATURE_TRANSLATION.put("sum_red", "红球总和");
        FEATURE_TRANSLATION.put("span_red", "红球跨度");
        FEATURE_TRANSLATION.put("odd_count", "奇数个数");
        FEATURE_TRANSLATION.put("even_count", "偶数个数");
        FEATURE_TRANSLATION.put("big_count", "大数个数");
        FEATURE_TRANSLATION.put("small_count", "小数个数");
        FEATURE_TRANSLATION.put("hot_hits", "热号命中");
        FEATURE_TRANSLATION.put("cold_hits", "冷号命中");
        FEATURE_TRANSLATION.put("blue_hot", "蓝球热度");
        FEATURE_TRANSLATION.put("red_sum_last_diff", "红球总和差值");
        FEATURE_TRANSLATION.put("red_max_last_diff", "红球最大值差值");
        FEATURE_TRANSLATION.put("consecutive_count", "连号个数");
        FEATURE_TRANSLATION.put("same_tail_count", "同尾号个数");
        
        // 模型名称翻译
        MODEL_TRANSLATION.put("RandomForest_Model_Prediction", "随机森林模型预测");
        MODEL_TRANSLATION.put("XGBoost_Model_Prediction", "XGBoost模型预测");
        MODEL_TRANSLATION.put("LSTM_Deep_Learning_Model_Prediction", "LSTM深度学习模型预测");
        MODEL_TRANSLATION.put("Transformer_Attention_Model_Prediction", "Transformer注意力模型预测");
        MODEL_TRANSLATION.put("Markov_Chain_Model_Prediction", "马尔可夫链模型预测");
        MODEL_TRANSLATION.put("Voting_Ensemble_Model_Prediction", "投票融合模型预测");
        MODEL_TRANSLATION.put("Stacking_Ensemble_Model_Prediction", "堆叠融合模型预测");
        MODEL_TRANSLATION.put("Second_Layer_Fusion_Model", "二层融合模型");
        
        // 其他术语翻译
        MODEL_TRANSLATION.put("Probability", "概率");
        MODEL_TRANSLATION.put("Key_Features", "关键特征");
        MODEL_TRANSLATION.put("importance", "重要度");
        MODEL_TRANSLATION.put("Main_Contributors", "主要贡献");
        MODEL_TRANSLATION.put("weight", "权重");
        MODEL_TRANSLATION.put("prob", "概率");
        MODEL_TRANSLATION.put("Ensemble", "集成");
        MODEL_TRANSLATION.put("Models", "个模型");
    }

    /**
     * 翻译预测结果中的reason字段
     *
     * @param reason 英文reason
     * @return 中文reason
     */
    public static String translateReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            return reason;
        }

        String translatedReason = reason;

        // 1. 翻译模型名称
        for (Map.Entry<String, String> entry : MODEL_TRANSLATION.entrySet()) {
            translatedReason = translatedReason.replace(entry.getKey(), entry.getValue());
        }

        // 2. 翻译特征名称
        for (Map.Entry<String, String> entry : FEATURE_TRANSLATION.entrySet()) {
            translatedReason = translatedReason.replace(entry.getKey(), entry.getValue());
        }

        // 3. 处理特殊格式的翻译
        translatedReason = translateSpecialFormats(translatedReason);

        return translatedReason;
    }

    /**
     * 处理特殊格式的翻译
     */
    private static String translateSpecialFormats(String text) {
        // 翻译 "特征名=值(importance:重要度)" 格式
        Pattern featurePattern = Pattern.compile("(\\w+)=(\\d+\\.\\d+)\\(importance:(\\d+\\.\\d+)\\)");
        Matcher matcher = featurePattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        
        while (matcher.find()) {
            String featureName = matcher.group(1);
            String value = matcher.group(2);
            String importance = matcher.group(3);
            
            // 翻译特征名称
            String translatedFeature = FEATURE_TRANSLATION.getOrDefault(featureName, featureName);
            
            String replacement = String.format("%s=%s(重要度:%s)", translatedFeature, value, importance);
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        
        // 翻译 "模型名(weight:权重,prob:概率)" 格式
        Pattern contributorPattern = Pattern.compile("(\\w+)\\(weight:(\\d+\\.\\d+),prob:(\\d+\\.\\d+)\\)");
        Matcher contributorMatcher = contributorPattern.matcher(sb.toString());
        StringBuffer sb2 = new StringBuffer();
        
        while (contributorMatcher.find()) {
            String modelName = contributorMatcher.group(1);
            String weight = contributorMatcher.group(2);
            String prob = contributorMatcher.group(3);
            
            String replacement = String.format("%s(权重:%s,概率:%s)", modelName, weight, prob);
            contributorMatcher.appendReplacement(sb2, replacement);
        }
        contributorMatcher.appendTail(sb2);
        
        return sb2.toString();
    }

    /**
     * 获取特征的中文名称
     */
    public static String getFeatureChineseName(String englishName) {
        return FEATURE_TRANSLATION.getOrDefault(englishName, englishName);
    }

    /**
     * 获取所有特征的中英文对照
     */
    public static Map<String, String> getAllFeatureTranslations() {
        return new HashMap<>(FEATURE_TRANSLATION);
    }
}
