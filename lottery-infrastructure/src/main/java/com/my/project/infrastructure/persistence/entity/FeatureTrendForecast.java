package com.my.project.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_feature_trend_forecast")
public class FeatureTrendForecast {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String sourcePeriod;
    private String targetPeriod;
    private String featureCode;
    private String modelName;
    private String candidateValues;
    private String detailJson;
    private Instant generatedAt;
}
