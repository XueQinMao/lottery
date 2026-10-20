package com.my.project.application.feature;

import com.my.project.application.feature.dto.ZoneTrendAnalysisCommand;
import com.my.project.application.feature.cache.LotteryAnalysisMultiLevelCache;
import com.my.project.application.history.HistoryRecordService;
import com.my.project.application.report.LotteryReportService;
import com.my.project.application.history.vo.DrawTrendVo;
import com.my.project.application.feature.support.ZoneTrendImages;
import com.my.project.llm.bo.LotteryAnalysisRespBo;
import com.my.project.llm.bo.ZoneTrendAnalysisBo;
import com.my.project.llm.bo.ZoneTrendSnapshot;
import com.my.project.llm.service.ZoneTrendModelService;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ZoneTrendAnalysisService {
    private final HistoryRecordService history;
    private final ZoneTrendModelService model;
    private final LotteryAnalysisMultiLevelCache cache;
    private final LotteryReportService reports;

    public LotteryAnalysisRespBo analyze(ZoneTrendAnalysisCommand command) {
        Assert.notNull(command, "请提交走势图和逐期数据");
        Assert.notNull(command.snapshot(), "缺少逐期特征数据");
        String cutoff = command.snapshot().endPeriod();
        Assert.isTrue(cutoff != null && cutoff.matches("[0-9]{7}"), "截止期号应为7位数字");
        var facts = snapshot(history.analyzeDrawTrends(30, cutoff));
        Assert.isTrue(cutoff.equals(facts.endPeriod()) && facts.equals(command.snapshot()),
            "图片所用数据与后台截止期数据不一致，请重新加载并生成图片");
        var imageInfo = ZoneTrendImages.validate(command.images());
        String key = "zone-trend-v3-" + cutoff + "-" + DigestUtil.sha256Hex(
            model.modelIdentity() + JSON.toJSONString(facts) + JSON.toJSONString(imageInfo)).substring(0, 24);
        return cache.get(key, ignored -> {
            var inference = model.analyze(facts, command.images());
            var result = LotteryAnalysisRespBo.builder().zoneTrendAnalysis(new ZoneTrendAnalysisBo(
                model.modelName(), Instant.now().toString(), key + ".json", facts, imageInfo, inference)).build();
            reports.saveAnalysis(result, key);
            return result;
        });
    }

    private static ZoneTrendSnapshot snapshot(DrawTrendVo data) {
        Assert.isTrue(data != null && data.periods().size() == 30, "三区走势试点需要完整的最近30期开奖");
        return new ZoneTrendSnapshot(data.periods().getLast(), data.periods(), series(data, "zones"), series(data, "threeZone"));
    }

    private static java.util.List<ZoneTrendSnapshot.Series> series(DrawTrendVo data, String code) {
        return data.redFeatures().stream().filter(group -> code.equals(group.code())).findFirst().orElseThrow()
            .series().stream().map(s -> new ZoneTrendSnapshot.Series(s.code(), s.values())).toList();
    }
}
