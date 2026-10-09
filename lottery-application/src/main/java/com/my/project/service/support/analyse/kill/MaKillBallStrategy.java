package com.my.project.service.support.analyse.kill;

import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.service.support.analyse.MaAnalysisUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 均线杀球。当前不参与硬杀汇总，与原 {@code splitRedKillBySource} 将均线置空一致。
 */
public class MaKillBallStrategy implements KillBallStrategy {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public String name() {
        return "均线杀球";
    }

    @Override
    public Optional<String> splitType() {
        return Optional.of("jx");
    }

    @Override
    public KillBallStrategyResult calculate(List<HistoryRecord> historyRecords,
        Triple<List<Integer>, String, Integer> nextWinBalls) {
        var past100 = historyRecords.subList(0, 100);
        var analyze = MaAnalysisUtils.analyze(past100);
        var redKills = analyze.getRedBalls().stream().filter(b -> Objects.equals("杀号", b.getAction()))
            .map(r -> Pair.of(r.getBall(), r.getConfidence())).sorted(Map.Entry.comparingByValue())
            .map(KillBallSupport.boMapper("均线杀球")).toList();
        var blueKills = analyze.getBlueBalls().stream().filter(b -> Objects.equals("杀号", b.getAction()))
            .map(r -> Pair.of(r.getBall(), r.getConfidence())).map(KillBallSupport.boMapper("均线杀球")).toList();
        return new KillBallStrategyResult(redKills, blueKills);
    }
}
