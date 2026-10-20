package com.my.project.application;

import com.my.project.application.support.NextLotteryDateUtils;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.infrastructure.persistence.repository.HistoryRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = CombinedKillRuleTest.DatabaseConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "kill.backtest.production", matches = "true")
class ProductionKillBacktestTest {
    private static final int WARMUP = 100;
    private static final int VALIDATION_SIZE = 600;

    private final HistoryRecordRepository repository;

    @Autowired
    ProductionKillBacktestTest(HistoryRecordRepository repository) {
        this.repository = repository;
    }

    @Test
    void backtestCurrentRedAndBlueRules() throws IOException {
        var history = repository.lambdaQuery().eq(HistoryRecord::getType, "SSQ")
                .orderByAsc(HistoryRecord::getPeriod).list();
        int split = history.size() - VALIDATION_SIZE;
        assertTrue(split > WARMUP, "历史期数必须覆盖预热、训练和验证");
        validateHistory(history);
        var periods = new ArrayList<ProductionKillRuleBreakdown.Period>(history.size() - WARMUP);
        var researched = new ArrayList<ProductionKillRuleBreakdown.Period>(history.size() - WARMUP);
        for (int i = WARMUP; i < history.size(); i++) {
            var past = history.subList(0, i).reversed();
            var production = ProductionKillRuleBreakdown.calculate(past, history.get(i));
            periods.add(production);
            researched.add(ResearchedKillRules.calculate(past, production));
        }
        writeReport(history, periods, researched, split);
    }

    private static void writeReport(List<HistoryRecord> history, List<ProductionKillRuleBreakdown.Period> periods,
                                    List<ProductionKillRuleBreakdown.Period> researched,
                                    int split) throws IOException {
        var report = Path.of("target", "surefire-reports", "production-kill-backtest.md");
        Files.createDirectories(report.getParent());
        int calibrationSize = split - WARMUP;
        try (var out = new PrintWriter(Files.newBufferedWriter(report, StandardCharsets.UTF_8))) {
            out.println("# 双色球现用杀号规则回测\n");
            out.printf("本地数据库取 %d 期。%s。%s。%s。%n%n", history.size(),
                    range("预热", history.subList(0, WARMUP)), range("训练", history.subList(WARMUP, split)),
                    range("验证", history.subList(split, history.size())));
            out.println("每期只使用更早的开奖，日期规则沿用生产的周二、周四、周日推算。"
                    + "生产建议逐期核对后在各色号码池内去重，越界结果不计作有效杀号。"
                    + "按期错杀表示至少排除一个实际中奖号码，联合错杀表示任一颜色错杀。"
                    + "随机基准按每期实际排除数量计算，再对各期取平均。\n");
            long dateDifferences = 0;
            for (int i = WARMUP; i < history.size(); i++) {
                if (!NextLotteryDateUtils.nextDrawDateAfter(history.get(i - 1).getOpenDate())
                        .equals(history.get(i).getOpenDate())) dateDifferences++;
            }
            out.printf("推算开奖日与实际日期不同的期数为 %d。%n%n", dateDifferences);
            ProductionKillBacktestReport.write(out, periods.subList(0, calibrationSize),
                    periods.subList(calibrationSize, periods.size()));
            ProductionKillBacktestReport.writeResearch(out, periods, researched, calibrationSize);
            assertTrue(!out.checkError(), "回测报告写入失败");
        }
        System.out.println(report.toAbsolutePath());
    }

    private static String range(String label, List<HistoryRecord> rows) {
        var first = rows.getFirst();
        var last = rows.getLast();
        return "%s %d 期，%s–%s（%s 至 %s）".formatted(label, rows.size(), first.getPeriod(), last.getPeriod(),
                first.getOpenDate(), last.getOpenDate());
    }

    private static void validateHistory(List<HistoryRecord> history) {
        for (int i = 0; i < history.size(); i++) {
            var row = history.get(i);
            var reds = ProductionKillRuleBreakdown.reds(row);
            assertEquals(6, reds.size(), row.getPeriod());
            assertTrue(reds.stream().allMatch(n -> n >= 1 && n <= 33), row.getPeriod());
            assertTrue(row.getSpecial() >= 1 && row.getSpecial() <= 16, row.getPeriod());
            if (i == 0) continue;
            var previous = history.get(i - 1);
            assertTrue(row.getOpenDate().isAfter(previous.getOpenDate()), row.getPeriod());
            int previousPeriod = Integer.parseInt(previous.getPeriod());
            int period = Integer.parseInt(row.getPeriod());
            int expected = period / 1000 == previousPeriod / 1000 ? previousPeriod + 1 : (previousPeriod / 1000 + 1) * 1000 + 1;
            assertEquals(expected, period, "缺失或重复的期号");
        }
    }
}
