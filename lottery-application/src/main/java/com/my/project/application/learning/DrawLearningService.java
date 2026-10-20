package com.my.project.application.learning;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.my.project.application.feature.dto.FeatureTrendForecast.Summary;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.learning.LearningPolicy.Track;
import com.my.project.application.learning.LearningReport.Experiment;
import com.my.project.application.learning.LearningReport.Outcome;
import com.my.project.application.review.DrawReview;
import com.my.project.application.support.NextLotteryDateUtils;
import com.my.project.infrastructure.persistence.repository.DrawLearningRepository;
import com.my.project.llm.bo.HybridRecommendationInput;
import com.my.project.llm.bo.LotteryAdjustRespBo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DrawLearningService {
    private static final TypeReference<Map<String, Track>> TRACKS = new TypeReference<>() { };
    private final DrawLearningRepository repository;
    private final HistoryRecordService history;

    @Transactional
    public Optional<List<String>> publishFeature(Summary forecast) {
        String period = forecast.targetPeriod();
        String scope = forecast.featureCode();
        var tracks = lockTracks();
        var existing = repository.findPrediction(period, scope).map(DrawLearningService::readFeature);
        if (existing.isPresent()) return existing.map(LearningPrediction.Feature::formalValues);
        if (forecast.values().isEmpty() || !canPublish(forecast.sourcePeriod(), period, Instant.now())) {
            return Optional.empty();
        }
        var track = tracks.getOrDefault(scope, Track.initial());
        var formal = reorder(forecast.values(), track.active().change());
        var trial = track.trial();
        var challenger = trial == null ? List.<String>of() : reorder(forecast.values(), trial.change());
        // 首次快照锁定本期比较版本，重复生成不能替换已经提交的试验样本。
        var snapshot = new LearningPrediction.Feature(Instant.now(), track.active(), trial, formal, challenger);
        if (!canPublish(forecast.sourcePeriod(), period, snapshot.publishedAt())) return Optional.empty();
        repository.savePrediction(period, scope, JSON.toJSONString(snapshot));
        return Optional.of(formal);
    }

    @Transactional
    public LotteryAdjustRespBo publishNumbers(String source, HybridRecommendationInput input, LotteryAdjustRespBo original) {
        var tracks = lockTracks();
        var period = input.targetPeriod();
        if (!canPublish(source, period, Instant.now())) return original;
        var stored = repository.findPrediction(period, LearningPolicy.NUMBERS).map(DrawLearningService::readNumbers);
        var track = tracks.getOrDefault(LearningPolicy.NUMBERS, Track.initial());
        var active = stored.map(LearningPrediction.Numbers::active).orElse(track.active());
        var trial = stored.isPresent() ? stored.orElseThrow().trial() : track.trial();
        long seed = ThreadLocalRandom.current().nextLong();
        var results = NumberLearning.variants(input, original, active.change(), trial, seed);
        var snapshot = new LearningPrediction.Numbers(Instant.now(), active, trial, results.formal(),
            results.trial(), seed, ThreadLocalRandom.current().nextLong());
        if (!canPublish(source, period, snapshot.publishedAt())) return original;
        if (stored.isEmpty()) repository.savePrediction(period, LearningPolicy.NUMBERS, JSON.toJSONString(snapshot));
        return results.formal();
    }

    public Optional<List<String>> formalFeature(String period, String code) {
        return repository.findPrediction(period, code).map(DrawLearningService::readFeature)
            .map(LearningPrediction.Feature::formalValues);
    }

    public Map<String, List<String>> formalFeatures(String period) {
        return repository.findPredictions(period).stream().filter(row -> !LearningPolicy.NUMBERS.equals(row.scope()))
            .collect(Collectors.toMap(DrawLearningRepository.Prediction::scope, row -> readFeature(row.payload()).formalValues()));
    }

    public Optional<DrawReview> archivedReview(String period) {
        return repository.findReview(period).map(json -> JSON.parseObject(json, DrawReview.class));
    }

    public List<String> pendingPeriods() {
        return repository.pendingPeriods(LearningPolicy.WINDOW);
    }

    @Transactional
    public void settle(DrawReview review) {
        var period = review.draw().period();
        var tracks = lockTracks();
        // 锁定策略状态，并在同一事务写入期号归档，保证重试不会重复累计成绩。
        if (repository.findReview(period).isPresent()) return;

        var features = review.features().stream().collect(Collectors.toMap(DrawReview.Feature::code, feature -> feature));
        var evaluations = new TreeMap<String, Experiment>();
        for (var prediction : repository.findPredictions(period)) {
            var scope = prediction.scope();
            if (LearningPolicy.NUMBERS.equals(scope)) {
                settleNumbers(review.draw(), readNumbers(prediction.payload()), tracks, evaluations);
            } else {
                settleFeature(review.draw(), features.get(scope), readFeature(prediction.payload()), tracks, evaluations);
            }
        }
        var recent = new ArrayList<DrawReview>();
        recent.add(review);
        repository.recentReviews(period, LearningPolicy.WINDOW - 1).forEach(json -> recent.add(JSON.parseObject(json, DrawReview.class)));
        LearningPolicy.propose(tracks, recent);
        tracks.forEach((scope, track) -> {
            var evaluation = evaluations.get(scope);
            if (track.trial() != null && (evaluation == null || evaluation.trialVersion() == 0)) {
                addEvaluation(scope, track.active(), track.trial(), track, "WAITING",
                    evaluation == null ? null : evaluation.outcome(), evaluations);
            }
        });
        var report = new LearningReport(LearningPolicy.WINDOW, List.copyOf(evaluations.values()));
        repository.saveState(JSON.toJSONString(tracks));
        repository.saveReview(period, JSON.toJSONString(review.withLearning(report)));
    }

    private static void settleFeature(DrawReview.Draw draw, DrawReview.Feature feature, LearningPrediction.Feature snapshot,
                                      Map<String, Track> tracks, Map<String, Experiment> evaluations) {
        if (feature == null || !snapshot.publishedAt().isBefore(DrawDeadline.at(draw.openDate()))) return;
        var formalRank = rank(snapshot.formalValues(), feature.actual());
        var trialRank = rank(snapshot.trialValues(), feature.actual());
        var outcome = new Outcome(snapshot.formalValues(), snapshot.trialValues(), formalRank, trialRank, null, null, null);
        Double delta = feature.actual() != null && snapshot.trial() != null && snapshot.formalValues().size() >= 2
            ? (double) (firstHit(trialRank) - firstHit(formalRank)) : null;
        evaluate(feature.code(), draw.period(), snapshot.active(), snapshot.trial(), delta, outcome, tracks, evaluations);
    }

    private static void settleNumbers(DrawReview.Draw draw, LearningPrediction.Numbers snapshot,
                                      Map<String, Track> tracks, Map<String, Experiment> evaluations) {
        if (!snapshot.publishedAt().isBefore(DrawDeadline.at(draw.openDate()))) return;
        var formal = NumberLearning.score(draw, snapshot.formal());
        var challenger = snapshot.challenger() == null ? null : NumberLearning.score(draw, snapshot.challenger());
        var random = NumberLearning.randomScore(draw, Math.toIntExact(formal.bets()), snapshot.randomSeed());
        var outcome = new Outcome(null, null, null, null, formal, challenger, random);
        Double delta = challenger != null && challenger.bets() == formal.bets()
            ? challenger.averageRedHits() - formal.averageRedHits() : null;
        evaluate(LearningPolicy.NUMBERS, draw.period(), snapshot.active(), snapshot.trial(), delta, outcome, tracks, evaluations);
    }

    private static void evaluate(String scope, String period, LearningPolicy.Version formal, LearningPolicy.Version trial,
                                 Double delta, Outcome outcome, Map<String, Track> tracks, Map<String, Experiment> evaluations) {
        var track = tracks.getOrDefault(scope, Track.initial());
        String status = trial == null ? "BASELINE" : "WAITING";
        if (trial != null && trial.equals(track.trial()) && formal.equals(track.active()) && delta != null) {
            track = LearningPolicy.record(scope, track, delta, period);
            tracks.put(scope, track);
            status = track.trial() != null ? "RUNNING" : track.lastDecision().promoted() ? "PROMOTED" : "REJECTED";
        }
        addEvaluation(scope, formal, trial, track, status, outcome, evaluations);
    }

    private static void addEvaluation(String scope, LearningPolicy.Version formal, LearningPolicy.Version trial, Track track,
                                      String status, Outcome outcome, Map<String, Experiment> evaluations) {
        var progress = track.progress();
        int periods = trial == null ? 0 : progress.periods();
        evaluations.put(scope, new Experiment(scope, trial == null ? formal.change() : trial.change(), formal.number(),
            trial == null ? 0 : trial.number(), periods, trial == null ? 0 : progress.wins(),
            trial == null ? 0 : progress.losses(), periods == 0 ? 0 : progress.sumDelta() / periods, status, outcome));
    }

    private Map<String, Track> lockTracks() {
        return new TreeMap<>(JSON.parseObject(repository.lockState(), TRACKS));
    }

    private boolean canPublish(String source, String target, Instant now) {
        var rows = history.getLatestRecords(1);
        if (rows.isEmpty() || !source.equals(rows.getFirst().getPeriod()) || target.compareTo(source) <= 0) return false;
        var date = NextLotteryDateUtils.nextDrawDateAfter(rows.getFirst().getOpenDate());
        return now.isBefore(DrawDeadline.at(date));
    }

    private static List<String> reorder(List<String> values, String change) {
        if (!LearningPolicy.SWAP.equals(change) || values.size() < 2) return values;
        var reordered = new ArrayList<>(values);
        Collections.swap(reordered, 0, 1);
        return List.copyOf(reordered);
    }

    private static Integer rank(List<String> values, String actual) {
        if (actual == null || values.isEmpty()) return null;
        int index = values.indexOf(actual);
        return index < 0 ? null : index + 1;
    }

    private static int firstHit(Integer rank) {
        return Integer.valueOf(1).equals(rank) ? 1 : 0;
    }

    private static LearningPrediction.Feature readFeature(String payload) {
        return JSON.parseObject(payload, LearningPrediction.Feature.class);
    }

    private static LearningPrediction.Numbers readNumbers(String payload) {
        return JSON.parseObject(payload, LearningPrediction.Numbers.class);
    }
}
