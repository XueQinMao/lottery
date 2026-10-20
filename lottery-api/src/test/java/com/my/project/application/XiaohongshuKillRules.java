package com.my.project.application;

import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.application.support.analyse.UnifiedKillBallRules;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;

/** 小红书筛选后保留的两项红球规则；公式与生产计算共用。 */
final class XiaohongshuKillRules {
    private XiaohongshuKillRules() { }

    static List<ToutiaoKillRules.Rule> catalog() {
        return ToutiaoKillRules.adapt(UnifiedKillBallRules.from(UnifiedKillBallRules.XIAOHONGSHU));
    }

    static Map<String, ToutiaoKillRules.Stat> backtest(List<HistoryRecord> records, int sampleSize, PrintStream out) {
        return backtest(records, sampleSize, out, catalog());
    }

    static Map<String, ToutiaoKillRules.Stat> backtest(List<HistoryRecord> records, int sampleSize, PrintStream out,
                                                     List<ToutiaoKillRules.Rule> rules) {
        out.printf("小红书回测：仅保留已整合的%d项红球规则；使用生产共享实现。%n", rules.size());
        return ToutiaoKillRules.backtest(records, sampleSize, out, rules, "小红书");
    }
}
