package com.my.project.application.feature;

import com.alibaba.fastjson2.JSON;
import com.my.project.application.feature.dto.FeatureTrendForecast.*;
import com.my.project.application.feature.support.ForecastTargetPeriod;
import com.my.project.application.feature.support.FeatureTrendPreparation;
import com.my.project.application.learning.DrawLearningService;
import com.my.project.infrastructure.persistence.repository.FeatureTrendForecastRepository;
import com.my.project.llm.bo.FeatureTrendInference.Candidate;
import com.my.project.llm.bo.FeatureTrendInference;
import com.my.project.llm.bo.FeatureTrendKind;
import com.my.project.llm.service.ZoneTrendModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class FeatureTrendForecastService {
    private final FeatureTrendPreparation preparation;
    private final FeatureTrendForecastRepository repository;
    private final ZoneTrendModelService model;
    private final DrawLearningService learning;
    private final ConcurrentHashMap<ForecastKey, CompletableFuture<Summary>> inFlight = new ConcurrentHashMap<>();

    public Plan prepare() {
        return prepare(null);
    }

    public Plan prepare(String endPeriod) {
        return preparation.prepare(endPeriod == null ? null : ForecastTargetPeriod.validate(endPeriod));
    }

    public Summary analyze(Run request) {
        Assert.notNull(request, "请提交待分析特征");
        Assert.notNull(request.input(), "缺少逐期走势数据");
        var kind = FeatureTrendKind.fromCode(request.featureCode());
        String source = ForecastTargetPeriod.validate(request.input().endPeriod());

        var key = new ForecastKey(source, kind);
        var current = new CompletableFuture<Summary>();
        // 首次查库前原子领取执行权。同一截止期和特征的并发请求共用一次结果。
        var previous = inFlight.putIfAbsent(key, current);
        if (previous != null) return await(previous);
        try {
            var result = repository.findSummary(source, kind.code()).map(FeatureTrendForecastService::summary)
                .orElseGet(() -> analyzeAndSave(request, kind, source));
            var formal = learning.publishFeature(result);
            if (formal.isPresent()) result = withValues(result, formal.orElseThrow());
            current.complete(result);
            return result;
        } catch (RuntimeException | Error failure) {
            current.completeExceptionally(failure);
            throw failure;
        } finally {
            inFlight.remove(key, current);
        }
    }

    private Summary analyzeAndSave(Run request, FeatureTrendKind kind, String source) {
        var prepared = preparation.prepare(request, kind, source);
        var inference = model.analyzeFeature(kind, prepared.input(), request.images());
        var values = inference.candidates().stream().map(Candidate::value).toList();
        var row = new FeatureTrendForecastRepository.Summary(source, prepared.targetPeriod(), kind.code(), model.modelName(),
            JSON.toJSONString(values), Instant.now());
        var detail = new FeatureTrendForecastRepository.Detail(row, JSON.toJSONString(new Evidence(prepared.input(), prepared.imageInfo(), inference)));
        return summary(repository.insertIfAbsent(detail));
    }

    public List<String> recentPeriods(String before, int limit) {
        Assert.isTrue(limit >= 1 && limit <= 50, "每页期数应为1至50");
        return repository.findRecentPeriods(before == null ? "9999999" : ForecastTargetPeriod.validate(before), limit);
    }

    public List<Summary> findPeriod(String period) {
        var rows = repository.findByPeriod(ForecastTargetPeriod.validate(period));
        if (rows.isEmpty()) return List.of();
        var formal = learning.formalFeatures(period);
        return rows.stream().map(FeatureTrendForecastService::summary)
            .map(row -> withValues(row, formal.getOrDefault(row.featureCode(), row.values()))).toList();
    }

    public Optional<Detail> findDetail(String period, String featureCode) {
        var kind = FeatureTrendKind.fromCode(featureCode);
        return repository.findDetail(ForecastTargetPeriod.validate(period), kind.code())
            .map(row -> {
                var raw = summary(row.summary());
                var formal = learning.formalFeature(period, featureCode).orElse(raw.values());
                var evidence = JSON.parseObject(row.detailJson(), Evidence.class);
                if (raw.values().equals(formal)) return new Detail(raw, evidence);
                var inference = evidence.inference();
                Map<String, Candidate> candidates = new java.util.HashMap<>();
                inference.candidates().forEach(candidate -> candidates.put(candidate.value(), candidate));
                var ordered = formal.stream().map(candidates::get).toList();
                var updated = new FeatureTrendInference(inference.observation(), ordered, inference.counterEvidence());
                return new Detail(withValues(raw, formal), new Evidence(evidence.input(), evidence.images(), updated));
            });
    }

    private static Summary withValues(Summary raw, List<String> values) {
        return new Summary(raw.sourcePeriod(), raw.targetPeriod(), raw.featureCode(), raw.label(), raw.blue(),
            raw.model(), values, raw.generatedAt());
    }

    private static Summary summary(FeatureTrendForecastRepository.Summary row) {
        var kind = FeatureTrendKind.fromCode(row.featureCode());
        return new Summary(row.sourcePeriod(), row.targetPeriod(), row.featureCode(), kind.label(), kind.blue(),
            row.model(), List.copyOf(JSON.parseArray(row.valuesJson(), String.class)), row.generatedAt());
    }

    private static Summary await(CompletableFuture<Summary> previous) {
        try {
            return previous.join();
        } catch (CompletionException failure) {
            if (failure.getCause() instanceof RuntimeException cause) throw cause;
            if (failure.getCause() instanceof Error cause) throw cause;
            throw failure;
        }
    }

    private record ForecastKey(String source, FeatureTrendKind kind) { }
}
