package com.my.project.service;

import com.my.project.llm.bo.FeatureForecastBo;
import com.my.project.llm.bo.LotteryAnalysisReqBo;
import com.my.project.llm.bo.LotteryAdjustViewBo.FeatureHit;
import com.my.project.persistence.entity.HistoryRecord;
import com.my.project.persistence.repository.IHistoryRecordRepository;
import com.my.project.service.llm.impl.LotteryFeatureAnalysisServiceImpl;
import com.my.project.service.support.FeatureForecastHitUtils;
import com.my.project.service.support.LotteryFeatureTrendUtils.FeatureKind;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * LotteryFeatureAnalysisServiceTest
 *
 * @author 刘强
 * @version 2026/08/19 14:51
 **/
@SpringBootTest
public class LotteryFeatureAnalysisServiceTest {

    @Resource
    private IHistoryRecordRepository historyRecordRepository;

    @Resource
    private LotteryFeatureAnalysisServiceImpl lotteryFeatureAnalysisService;

    /**
     * 这个单侧不要删除 后面要一直用
     * 取最近 31 期，得到 30 个「样本期末 → 下一期」对照：
     * 样本期末（如 2026066）往前 100 期算 featureForecastBo，用下一期（2026067）开奖对照命中。
     */
    @Test
    public void test_ananylsis() {
        List<HistoryRecord> window = historyRecordRepository.lambdaQuery()
            .orderByDesc(HistoryRecord::getOpenDate)
            .last("limit 31")
            .list();
        if (CollectionUtils.size(window) < 2) {
            throw new IllegalStateException("历史开奖不足，无法做下一期对照");
        }

        FeatureKind[] kinds = FeatureKind.values();
        int pairCount = Math.min(30, window.size() - 1);
        int[] mainHits = new int[kinds.length];
        int[] altHits = new int[kinds.length];
        int[] samples = new int[kinds.length];

        List<PeriodRow> rows = new ArrayList<>();
        List<String> missDetails = new ArrayList<>();

        // window[0] 最新，仅作验证期；从旧到新输出
        for (int i = pairCount; i >= 1; i--) {
            HistoryRecord end = window.get(i);
            HistoryRecord next = window.get(i - 1);

            List<HistoryRecord> past100 = historyRecordRepository.lambdaQuery()
                .le(HistoryRecord::getOpenDate, end.getOpenDate())
                .orderByDesc(HistoryRecord::getOpenDate)
                .last("limit 100")
                .list();
            if (CollectionUtils.size(past100) < 100) {
                System.out.printf("跳过 %s：往前不足 100 期（实际 %d）%n",
                    end.getPeriod(), CollectionUtils.size(past100));
                continue;
            }

            var forecastRecords = past100.stream().map(this::toDrawRecord).collect(Collectors.toList());
            FeatureForecastBo forecast = lotteryFeatureAnalysisService.forecastFeaturesByIndex(forecastRecords);

            List<Integer> reds = Arrays.asList(next.getNum1(), next.getNum2(), next.getNum3(),
                next.getNum4(), next.getNum5(), next.getNum6());
            List<FeatureHit> hits = FeatureForecastHitUtils.analyze(reds, next.getSpecial(), forecast);
            Map<String, FeatureHit> hitByCode = new HashMap<>();
            for (FeatureHit hit : hits) {
                if (hit.getCode() != null) {
                    hitByCode.put(hit.getCode(), hit);
                }
            }

            String[] cells = new String[kinds.length];
            int periodMain = 0;
            int periodAlt = 0;
            int periodMiss = 0;
            for (int k = 0; k < kinds.length; k++) {
                FeatureKind kind = kinds[k];
                FeatureHit hit = hitByCode.get(kind.getCode());
                samples[k]++;
                String type = hit == null ? FeatureForecastHitUtils.HIT_MISS : hit.getHitType();
                if (FeatureForecastHitUtils.HIT_MAIN.equals(type)) {
                    mainHits[k]++;
                    periodMain++;
                    cells[k] = "主";
                } else if (FeatureForecastHitUtils.HIT_ALT.equals(type)) {
                    altHits[k]++;
                    periodAlt++;
                    cells[k] = "备";
                } else {
                    periodMiss++;
                    cells[k] = "×";
                    String actual = hit == null ? "-" : nullToDash(hit.getActual());
                    String main = hit == null ? "-" : nullToDash(hit.getMainValue());
                    String alts = hit == null || CollectionUtils.isEmpty(hit.getAlternatives())
                        ? "-" : String.join("、", hit.getAlternatives());
                    missDetails.add(String.format("| %s | %s | %s | %s | %s | %s |",
                        end.getPeriod(), next.getPeriod(), kind.getLabel(), actual, main, alts));
                }
            }
            rows.add(new PeriodRow(end.getPeriod(), next.getPeriod(), cells, periodMain, periodAlt, periodMiss));
        }

        if (rows.isEmpty()) {
            throw new IllegalStateException("没有完成任何一期对照");
        }

        StringBuilder md = new StringBuilder();
        md.append("## 形态推算回测（样本期末往前 100 期 → 对照下一期）\n\n");
        md.append("- 对照期数：").append(rows.size()).append("\n");
        md.append("- 命中标记：`主` = 命中主推，`备` = 命中备选，`×` = 未命中\n\n");

        md.append("### 逐期命中\n\n");
        md.append("| 样本期末 | 验证期 |");
        for (FeatureKind kind : kinds) {
            md.append(' ').append(kind.getLabel()).append(" |");
        }
        md.append(" 主 | 备 | 未 | 含备选命中率 |\n");
        md.append("|---|---|");
        md.append("---|".repeat(kinds.length));
        md.append("---|---|---|---|\n");
        for (PeriodRow row : rows) {
            md.append("| ").append(row.endPeriod).append(" | ").append(row.nextPeriod).append(" |");
            for (String cell : row.cells) {
                md.append(' ').append(cell).append(" |");
            }
            int hitWithAlt = row.main + row.alt;
            int total = row.main + row.alt + row.miss;
            md.append(' ').append(row.main)
                .append(" | ").append(row.alt)
                .append(" | ").append(row.miss)
                .append(" | ").append(pct(hitWithAlt, total))
                .append(" |\n");
        }

        md.append("\n### 分特征命中率\n\n");
        md.append("| 形态 | 样本 | 主推命中 | 主推命中率 | 备选命中 | 含备选命中 | 含备选命中率 |\n");
        md.append("|---|---:|---:|---:|---:|---:|---:|\n");
        int allMain = 0;
        int allAlt = 0;
        int allSample = 0;
        for (int k = 0; k < kinds.length; k++) {
            int n = samples[k];
            int main = mainHits[k];
            int alt = altHits[k];
            allMain += main;
            allAlt += alt;
            allSample += n;
            md.append("| ").append(kinds[k].getLabel())
                .append(" | ").append(n)
                .append(" | ").append(main)
                .append(" | ").append(pct(main, n))
                .append(" | ").append(alt)
                .append(" | ").append(main + alt)
                .append(" | ").append(pct(main + alt, n))
                .append(" |\n");
        }
        md.append("| **合计** | ").append(allSample)
            .append(" | ").append(allMain)
            .append(" | ").append(pct(allMain, allSample))
            .append(" | ").append(allAlt)
            .append(" | ").append(allMain + allAlt)
            .append(" | ").append(pct(allMain + allAlt, allSample))
            .append(" |\n");

        if (!missDetails.isEmpty()) {
            md.append("\n### 未命中明细\n\n");
            md.append("| 样本期末 | 验证期 | 形态 | 实际 | 主推 | 备选 |\n");
            md.append("|---|---|---|---|---|---|\n");
            for (String line : missDetails) {
                md.append(line).append('\n');
            }
        }

        System.out.println(md);
    }

    private static String pct(int hit, int total) {
        if (total <= 0) {
            return "-";
        }
        return String.format(Locale.ROOT, "%.1f%%", hit * 100.0 / total);
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private LotteryAnalysisReqBo.DrawRecord toDrawRecord(HistoryRecord record) {
        List<Integer> redBalls =
            Arrays.asList(record.getNum1(), record.getNum2(), record.getNum3(), record.getNum4(), record.getNum5(),
                record.getNum6());
        return LotteryAnalysisReqBo.DrawRecord.builder().period(record.getPeriod()).redBalls(redBalls)
            .blueBall(record.getSpecial()).build();
    }

    private record PeriodRow(String endPeriod, String nextPeriod, String[] cells, int main, int alt, int miss) {
    }
}
