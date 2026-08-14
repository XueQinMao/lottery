package com.my.project.service.support;

import com.alibaba.fastjson2.JSON;
import com.my.project.service.history.pojo.vo.PatternTrendVo;
import com.my.project.service.history.pojo.vo.PatternTrendVo.RatioOption;
import com.my.project.service.history.pojo.vo.PatternTrendVo.Stats;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LotteryMorphologySnapshotUtils
 *
 * <p>把形态指数页同源的 {@link PatternTrendVo} 压成 LLM 快照：
 * 样本期数、最近一期、stats、ratioOptions 与页面接口完全一致。
 *
 * @author 刘强
 * @version 2026/08/14
 **/
public final class LotteryMorphologySnapshotUtils {

    private LotteryMorphologySnapshotUtils() {
    }

    public static String fromPatternTrend(PatternTrendVo vo) {
        if (vo == null || vo.getStats() == null) {
            throw new IllegalArgumentException("形态指数结果不能为空");
        }
        Stats stats = vo.getStats();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("feature", vo.getFeature());
        root.put("label", vo.getFeatureLabel());
        root.put("sampleSize", stats.getTotalPeriods());
        root.put("lastPeriod", vo.getLatestPeriod());
        root.put("lastWinning", vo.getLatestWinning());
        root.put("lastValue", vo.getLatestRatio());
        root.put("indexFormula", "index = hitCount − n×p；与形态指数页 stats / ratioOptions 同源");

        Map<String, Object> lastStats = new LinkedHashMap<>();
        lastStats.put("maxOmission", stats.getMaxOmission());
        lastStats.put("avgOmission", stats.getAvgOmission());
        lastStats.put("currentOmission", stats.getCurrentOmission());
        lastStats.put("hitCount", stats.getHitCount());
        lastStats.put("totalPeriods", stats.getTotalPeriods());
        lastStats.put("theoreticalProb", stats.getTheoreticalProb());
        lastStats.put("theoreticalHits", stats.getTheoreticalHits());
        lastStats.put("index", stats.getIndex());
        root.put("stats", lastStats);

        List<Map<String, Object>> options = new ArrayList<>();
        for (RatioOption opt : vo.getRatioOptions() == null ? List.<RatioOption>of() : vo.getRatioOptions()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ratio", opt.getRatio());
            row.put("hitCount", opt.getHitCount());
            row.put("theoreticalProb", opt.getTheoreticalProb());
            row.put("theoreticalHits", opt.getTheoreticalHits());
            row.put("index", opt.getIndex());
            row.put("currentOmission", opt.getCurrentOmission());
            row.put("avgOmission", opt.getAvgOmission());
            row.put("maxOmission", opt.getMaxOmission());
            row.put("isLast", opt.getRatio() != null && opt.getRatio().equals(vo.getLatestRatio()));
            options.add(row);
        }
        root.put("ratioOptions", options);
        return JSON.toJSONString(root);
    }
}
