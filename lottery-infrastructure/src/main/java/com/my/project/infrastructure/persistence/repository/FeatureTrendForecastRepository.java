package com.my.project.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.extension.service.IService;
import com.my.project.infrastructure.persistence.entity.FeatureTrendForecast;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FeatureTrendForecastRepository extends IService<FeatureTrendForecast> {
    List<Summary> findByPeriod(String targetPeriod);

    List<String> findRecentPeriods(String beforePeriod, int limit);

    Optional<Summary> findSummary(String sourcePeriod, String featureCode);

    Optional<Detail> findDetail(String targetPeriod, String featureCode);

    Summary insertIfAbsent(Detail detail);

    record Summary(String sourcePeriod, String targetPeriod, String featureCode, String model,
                   String valuesJson, Instant generatedAt) { }

    record Detail(Summary summary, String detailJson) { }
}
