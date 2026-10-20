package com.my.project.application.feature.support;

import com.my.project.application.feature.dto.FeatureTrendForecast.Item;
import com.my.project.application.feature.dto.FeatureTrendForecast.Plan;
import com.my.project.application.feature.dto.FeatureTrendForecast.Run;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.history.support.DrawTrendCalculator;
import com.my.project.application.history.vo.DrawTrendVo;
import com.my.project.application.support.analyse.ColdHotAnalysisUtils;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.llm.bo.FeatureTrendInput;
import com.my.project.llm.bo.FeatureTrendKind;
import com.my.project.llm.bo.ZoneTrendAnalysisBo.ImageInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class FeatureTrendPreparation {
    private final HistoryRecordService history;
    private final ForecastTargetPeriod targetPeriods;

    public Plan prepare(String endPeriod) {
        var sample = sample(endPeriod);
        var stats = history.analyzeFeatureStats(FeatureTrendInputs.SAMPLE_SIZE, sample.cutoff().getPeriod());
        Assert.isTrue(stats.getPeriods().equals(sample.trends().periods()), "特征统计与走势期号不一致，请刷新重试");
        var items = Arrays.stream(FeatureTrendKind.values()).map(kind ->
            new Item(kind.code(), kind.label(), kind.blue(), FeatureTrendInputs.select(sample.trends(), stats, kind))).toList();
        String target = targetPeriods.after(sample.cutoff());
        return new Plan(sample.cutoff().getPeriod(), target, items, ColdHotAnalysisUtils.calculate(sample.records()));
    }

    public Prepared prepare(Run request, FeatureTrendKind kind, String source) {
        var sample = sample(source);
        var stats = FeatureTrendInputs.needsStats(kind)
            ? history.analyzeFeatureStats(FeatureTrendInputs.SAMPLE_SIZE, source) : null;
        if (stats != null) {
            Assert.isTrue(stats.getPeriods().equals(sample.trends().periods()), "特征统计与走势期号不一致，请刷新重试");
        }
        FeatureTrendInput facts = FeatureTrendInputs.select(sample.trends(), stats, kind);
        Assert.isTrue(facts.equals(request.input()), "图片使用的数据与后台逐期数据不一致，请重新加载");
        var imageInfo = ZoneTrendImages.validateFeature(request.images(), FeatureTrendInputs.sheets(facts));
        return new Prepared(facts, imageInfo, targetPeriods.after(sample.cutoff()));
    }

    private Sample sample(String period) {
        int size = FeatureTrendInputs.SAMPLE_SIZE;
        var rows = history.getRecordsEndingAt(period, size + DrawTrendCalculator.COLD_HOT_WINDOW);
        Assert.isTrue(rows.size() >= size, "走势推演需要完整的最近30期开奖");
        HistoryRecord cutoff = rows.getFirst();
        Assert.isTrue(period == null || period.equals(cutoff.getPeriod()), "截止期与后台开奖记录不一致");
        return new Sample(cutoff, DrawTrendCalculator.calculate(rows, size), rows);
    }

    public record Prepared(FeatureTrendInput input, List<ImageInfo> imageInfo, String targetPeriod) { }

    private record Sample(HistoryRecord cutoff, DrawTrendVo trends, List<HistoryRecord> records) { }
}
