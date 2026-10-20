package com.my.project.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.my.project.infrastructure.persistence.entity.FeatureTrendForecast;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FeatureTrendForecastMapper extends BaseMapper<FeatureTrendForecast> {
    @Insert("""
        INSERT INTO t_feature_trend_forecast
            (source_period, target_period, feature_code, model_name, candidate_values, detail_json, generated_at)
        VALUES (#{sourcePeriod}, #{targetPeriod}, #{featureCode}, #{modelName}, #{candidateValues}, #{detailJson}, #{generatedAt})
        ON DUPLICATE KEY UPDATE id = t_feature_trend_forecast.id
        """)
    int insertIfAbsent(FeatureTrendForecast forecast);
}
