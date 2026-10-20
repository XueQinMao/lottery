package com.my.project.infrastructure.persistence.repository.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.my.project.infrastructure.persistence.entity.FeatureTrendForecast;
import com.my.project.infrastructure.persistence.mapper.FeatureTrendForecastMapper;
import com.my.project.infrastructure.persistence.repository.FeatureTrendForecastRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FeatureTrendForecastRepositoryImpl extends ServiceImpl<FeatureTrendForecastMapper, FeatureTrendForecast>
    implements FeatureTrendForecastRepository {

    @Override
    public List<Summary> findByPeriod(String targetPeriod) {
        return summaryQuery().eq(FeatureTrendForecast::getTargetPeriod, targetPeriod)
            .orderByAsc(FeatureTrendForecast::getFeatureCode)
            .list().stream().map(FeatureTrendForecastRepositoryImpl::toSummary).toList();
    }

    @Override
    public List<String> findRecentPeriods(String beforePeriod, int limit) {
        return lambdaQuery().select(FeatureTrendForecast::getTargetPeriod)
            .lt(FeatureTrendForecast::getTargetPeriod, beforePeriod)
            .groupBy(FeatureTrendForecast::getTargetPeriod)
            .orderByDesc(FeatureTrendForecast::getTargetPeriod)
            .page(Page.of(1, limit, false))
            .getRecords().stream().map(FeatureTrendForecast::getTargetPeriod).toList();
    }

    @Override
    public Optional<Summary> findSummary(String sourcePeriod, String featureCode) {
        return summaryQuery().eq(FeatureTrendForecast::getSourcePeriod, sourcePeriod)
            .eq(FeatureTrendForecast::getFeatureCode, featureCode)
            .oneOpt().map(FeatureTrendForecastRepositoryImpl::toSummary);
    }

    @Override
    public Optional<Detail> findDetail(String targetPeriod, String featureCode) {
        return lambdaQuery().eq(FeatureTrendForecast::getTargetPeriod, targetPeriod)
            .eq(FeatureTrendForecast::getFeatureCode, featureCode)
            .oneOpt().map(row -> new Detail(toSummary(row), row.getDetailJson()));
    }

    @Override
    public Summary insertIfAbsent(Detail detail) {
        var summary = detail.summary();
        getBaseMapper().insertIfAbsent(FeatureTrendForecast.builder()
            .sourcePeriod(summary.sourcePeriod())
            .targetPeriod(summary.targetPeriod())
            .featureCode(summary.featureCode())
            .modelName(summary.model())
            .candidateValues(summary.valuesJson())
            .detailJson(detail.detailJson())
            .generatedAt(summary.generatedAt())
            .build());
        return findSummary(summary.sourcePeriod(), summary.featureCode())
            .orElseThrow(() -> new IllegalStateException("目标期号已关联其他截止期，请刷新开奖记录"));
    }

    private LambdaQueryChainWrapper<FeatureTrendForecast> summaryQuery() {
        return lambdaQuery().select(FeatureTrendForecast::getSourcePeriod, FeatureTrendForecast::getTargetPeriod,
            FeatureTrendForecast::getFeatureCode, FeatureTrendForecast::getModelName,
            FeatureTrendForecast::getCandidateValues, FeatureTrendForecast::getGeneratedAt);
    }

    private static Summary toSummary(FeatureTrendForecast row) {
        return new Summary(row.getSourcePeriod(), row.getTargetPeriod(), row.getFeatureCode(),
            row.getModelName(), row.getCandidateValues(), row.getGeneratedAt());
    }
}
