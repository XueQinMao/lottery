package com.my.project.application.support.analyse.kill;

import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.application.support.analyse.IndexAnalysisUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 指数杀球。
 */
public class IndexKillBallStrategy implements KillBallStrategy {

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String name() {
        return "指数杀球";
    }

    @Override
    public Optional<String> splitType() {
        return Optional.of("zs");
    }

    @Override
    public KillBallStrategyResult calculate(List<HistoryRecord> historyRecords,
        Triple<List<Integer>, String, Integer> nextWinBalls) {
        var indexAnalyze = IndexAnalysisUtils.analyze(historyRecords);
        var killReds = indexAnalyze.getRedBalls().stream()
            .filter(r -> !(Objects.equals(r.getIndexTb(), "小") && r.getConfidence() >= 0.05))
            .map(r -> Pair.of(r.getBall(), r.getConfidence())).sorted(Map.Entry.comparingByValue())
            .map(KillBallSupport.boMapper("指数杀球")).toList();
        var killBlues = indexAnalyze.getBlueBalls().stream()
            .filter(r -> !(Objects.equals(r.getIndexTb(), "小") && r.getConfidence() >= 0.05))
            .map(r -> Pair.of(r.getBall(), r.getConfidence())).map(KillBallSupport.boMapper("指数杀球")).toList();
        return new KillBallStrategyResult(killReds, killBlues);
    }
}
