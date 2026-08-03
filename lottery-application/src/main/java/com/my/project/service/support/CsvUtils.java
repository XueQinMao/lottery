package com.my.project.service.support;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * CsvUtils
 *
 * @author 刘强
 * @version 2025/09/09 16:51
 **/
public class CsvUtils {

    public static final String PREDICT_RESULT_CSV_FILE_NAME ="predict_result.csv";
    public static final String WINNING_FEATURES_CSV_FILE_NAME ="winning_features.csv";
    public static final String WINNING_FEATURES_SENIOR_CSV_FILE_NAME ="winning_features_senior.csv";

    public static final StringBuilder SINGLE_CSV_HEADER =
        new StringBuilder().append("sum_value,span,odd_count,small_count,prime_count,")
            .append("avg_interval,interval_std,min_interval,max_interval,")
            .append("consecutive_count,has_consecutive,max_consecutive_length,")
            .append("zone1_count,zone2_count,zone3_count,max_zone_count,")
            .append("road0_count,road1_count,road2_count,").append("prime_ratio,odd_even_ratio,small_big_ratio,")
            // 🔵 蓝球基础特征
            .append("blue_ball,blue_odd,blue_small,")
            // 🔵 蓝球增强特征
            .append("blue_frequency,blue_frequency_ratio,blue_frequency_deviation,")
            .append("blue_gap_current,blue_gap_avg,blue_gap_deviation,")
            .append("blue_hot_10,blue_hot_20,blue_temperature,")
            .append("blue_position_norm,blue_group_1_4,blue_group_5_8,")
            .append("blue_group_9_12,blue_group_13_16,blue_is_prime,")
            .append("label1,label2,label3,label4,label5,label6,label_blue");

    public static final StringBuilder SENIOR_CSV_HEADER =
        new StringBuilder().append("sum_value,span,odd_count,small_count,prime_count,")
            .append("avg_interval,interval_std,min_interval,max_interval,")
            .append("consecutive_count,has_consecutive,max_consecutive_length,")
            .append("zone1_count,zone2_count,zone3_count,max_zone_count,")
            .append("road0_count,road1_count,road2_count,").append("prime_ratio,odd_even_ratio,small_big_ratio,")
            // 🔵 蓝球基础特征
            .append("blue_ball,blue_odd,blue_small,")
            // 🔵 蓝球增强特征
            .append("blue_frequency,blue_frequency_ratio,blue_frequency_deviation,")
            .append("blue_gap_current,blue_gap_avg,blue_gap_deviation,")
            .append("blue_hot_10,blue_hot_20,blue_temperature,")
            .append("blue_position_norm,blue_group_1_4,blue_group_5_8,")
            .append("blue_group_9_12,blue_group_13_16,blue_is_prime,")
            // 高级特征
            .append("combination_variance,combination_skewness,combination_kurtosis,")
            .append("combination_entropy,position_correlation,gap_distribution,cluster_density,").append(
                "historical_similarity,pattern_match_score,trend_alignment,probability_score,statistical_likelihood,randomness_measure,frequency_score,")
            .append("combination_score,combination_rank,combination_probability,combination_optimizer,")
            .append("label1,label2,label3,label4,label5,label6,label_blue");

    public static String PREDICT_RESULT_CSV_HEADER = "hitCount,blueHit,explanation,areaDistribution,probScore,combScore,ruleScore,totalScore,sumValue,oddCount,evenCount,bigCount,smallCount,primeCount,consecutiveCount\n";
    public static <T> void writeCsv(String filePath, String header, List<T> data, Function<T, String> convert)  throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            // 写入表头
            writer.write(header);
            // 注意：header已经包含换行符，不需要再次添加
            for (T t : data) {
                if(Objects.isNull(t)){
                    continue;
                }

                String row = convert.apply(t);
                if (row == null || row.isEmpty()) {
                    continue;
                }
                
                // 检查转换后的行是否包含明显的null值或格式问题
                if (isInvalidRow(row)) {
                    continue;
                }
                
                writer.write(row);
                // 注意：convert方法已经包含换行符，不需要再次添加
            }
        }
    }
    
    /**
     * 检查行数据是否有效
     * @param row CSV行数据
     * @return true表示无效，false表示有效
     */
    private static boolean isInvalidRow(String row) {
        // 检查是否包含null字符串
        if (row.contains("null")) {
            return true;
        }
        
        // 检查是否以逗号结尾（表示最后字段为空）
        if (row.endsWith(",")) {
            return true;
        }
        
        // 检查是否包含连续的逗号（表示中间有空字段）
        if (row.contains(",,")) {
            return true;
        }
        
        // 检查是否以逗号开头（表示第一个字段为空）
        if (row.startsWith(",")) {
            return true;
        }
        
        return false;
    }
}
