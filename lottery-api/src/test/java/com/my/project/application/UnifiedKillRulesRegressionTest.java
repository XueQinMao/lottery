package com.my.project.application;

import com.alibaba.fastjson2.JSON;
import com.my.project.application.support.analyse.KillBallUtils;
import com.my.project.application.support.analyse.UnifiedKillBallRules;
import com.my.project.application.support.analyse.RedBallKillUtils;
import com.my.project.application.support.analyse.kill.XieXiuKillBallStrategy;
import com.my.project.application.support.analyse.kill.KillBallSupport;
import com.my.project.llm.bo.KillNumberResultBo;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class UnifiedKillRulesRegressionTest {
    @Test
    void retainedFormulasMatchUserReportAcross98PeriodsAndProductionMatchesBacktest() throws Exception {
        try (var stream = getClass().getResourceAsStream("/kill/selected-rules-user-report.json")) {
            assertNotNull(stream);
            var rows = JSON.parseArray(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            List<HistoryRecord> history = new ArrayList<>();
            int checked = 0;
            for (int i = 0; i < rows.size(); i++) {
                var row = rows.getJSONObject(i);
                var red = row.getJSONArray("red").toJavaList(Integer.class);
                // 原日志未包含日期，测试日期只用于验证日期传递，不用来断言原日志的日期规则。
                LocalDate date = LocalDate.of(2026, 1, 1).plusDays(i);
                var target = HistoryRecord.builder().period(row.getString("period")).special(row.getInteger("blue"))
                        .openDate(date).num1(red.get(0)).num2(red.get(1)).num3(red.get(2))
                        .num4(red.get(3)).num5(red.get(4)).num6(red.get(5)).build();
                if (i >= 2) {
                    var context = new UnifiedKillBallRules.Context(history, date);
                    for (var rule : UnifiedKillBallRules.catalog()) {
                        assertEquals(row.getJSONObject("kills").getJSONArray(rule.id()).toJavaList(Integer.class),
                                rule.kills(context), row.getString("period") + " " + rule.id());
                        checked++;
                    }
                    if (i >= 95) {
                        var business = KillBallUtils.calculate(history, null, date);
                        for (boolean blue : List.of(false, true)) {
                            var items = blue ? business.getHardKillBlue() : business.getHardKillRed();
                            var expected = new TreeSet<Integer>();
                            List<KillNumberResultBo.KillItemBo> original = new ArrayList<>();
                            if (!blue) original.addAll(RedBallKillUtils.calculate(history, date));
                            else {
                                history.stream().limit(4).forEach(h -> original.add(KillBallSupport.kill("上期出现", h.getSpecial())));
                                Arrays.stream(UnifiedKillBallRules.reds(history.getFirst())).filter(n -> n <= 16)
                                    .forEach(n -> original.add(KillBallSupport.kill("上期开出-红球", n)));
                                original.addAll(new XieXiuKillBallStrategy().killBlue(history));
                            }
                            var baseline = original.stream().distinct().toList();
                            assertEquals(baseline.toString(), items.subList(0, baseline.size()).toString(),
                                    "原有号码、顺序、来源和分数必须保持不变");
                            baseline.forEach(item -> expected.add(item.getBall()));
                            for (var rule : UnifiedKillBallRules.catalog()) {
                                if (rule.blue() != blue) continue;
                                for (int number : rule.kills(context)) {
                                    expected.add(number);
                                    assertTrue(items.stream().anyMatch(item -> item.getBall() == number), rule.id());
                                }
                            }
                            assertEquals(new ArrayList<>(expected), items.stream().map(item -> item.getBall()).sorted().toList());
                        }
                    }
                }
                history.addFirst(target);
            }
            assertEquals(1176, checked);
        }
    }
}
