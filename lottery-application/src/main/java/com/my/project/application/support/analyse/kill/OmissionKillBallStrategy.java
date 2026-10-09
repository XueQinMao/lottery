package com.my.project.application.support.analyse.kill;

import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.application.support.OmissionDueProbabilityUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 遗漏杀球。当前不参与硬杀汇总，与原 {@code collectHardKillRed} 注释掉遗漏硬杀一致。
 */
public class OmissionKillBallStrategy implements KillBallStrategy {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public String name() {
        return "遗漏杀球";
    }

    @Override
    public Optional<String> splitType() {
        return Optional.of("yl");
    }

    @Override
    public KillBallStrategyResult calculate(List<HistoryRecord> historyRecords,
        Triple<List<Integer>, String, Integer> nextWinBalls) {
        var stringMapMap = OmissionDueProbabilityUtils.analyze(historyRecords);
        var killReds = stringMapMap.get("红球").entrySet().stream().filter(entry -> entry.getValue() <= 0)
            .map(e -> Pair.of(Integer.parseInt(e.getKey()), e.getValue())).sorted(Map.Entry.comparingByValue())
            .map(KillBallSupport.boMapper("遗漏杀球")).toList();
        var killBlues = stringMapMap.get("蓝球").entrySet().stream().filter(entry -> entry.getValue() <= 0)
            .map(e -> Pair.of(Integer.parseInt(e.getKey()), e.getValue())).sorted(Map.Entry.comparingByValue())
            .map(KillBallSupport.boMapper("遗漏杀球")).toList();
        return new KillBallStrategyResult(killReds, killBlues);
    }
}
