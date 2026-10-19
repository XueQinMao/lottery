package com.my.project.application.feature.support;

import com.alibaba.fastjson2.JSON;
import com.my.project.llm.bo.PatternFeatureAnalysisBo;
import com.my.project.llm.bo.PatternFeatureAnalysisReqBo;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** 三区比由各区候选个数组合生成，不独立调用模型或补造候选。 */
public final class ZoneCandidateConsistency {
    private ZoneCandidateConsistency() { }

    public static PatternFeatureAnalysisBo fromHistory(PatternFeatureAnalysisReqBo request) {
        return PatternFeatureAnalysisBo.builder().code(request.getCode()).label(request.getLabel())
            .source("derived-zone-counts").status("UNAVAILABLE")
            .sampleSize(request.getObservations().size()).observations(request.getObservations())
            .patternSummary("逐期特征：" + JSON.toJSONString(request.getObservations().stream()
                .map(PatternFeatureAnalysisBo.Observation::getValue).toList())
                + "\n形态描述：历史三区比由每期开奖按一区、二区、三区统计；下期组合由各区候选个数生成，不作独立模型推演。")
            .likelyValues(List.of()).unlikelyValues(List.of()).build();
    }

    public static void reconcile(List<PatternFeatureAnalysisBo> results) {
        var joint = find(results, "threeZone");
        if (joint == null) return;
        joint.setSource("derived-zone-counts");
        joint.setLikelyValues(List.of());
        joint.setUnlikelyValues(List.of());
        joint.setUnlikelyReason("未入选组合不代表暂不优先，不作额外排除。");
        var counts = List.of("zone1Count", "zone2Count", "zone3Count").stream()
            .map(code -> find(results, code)).toList();
        String failures = java.util.stream.IntStream.range(0, counts.size())
            .filter(i -> counts.get(i) == null || !"OK".equals(counts.get(i).getStatus())
                || counts.get(i).getLikelyValues() == null)
            .mapToObj(i -> counts.get(i) == null ? "第" + (i + 1) + "区分析缺失"
                : counts.get(i).getLabel() + "：" + Objects.toString(counts.get(i).getFailureReason(), "分析不可用"))
            .collect(Collectors.joining("；"));
        if (!failures.isEmpty()) {
            unavailable(joint, "三区比未生成：" + failures);
            return;
        }
        var pools = counts.stream().map(item -> item.getLikelyValues().stream()
            .map(Integer::parseInt).distinct().sorted().toList()).toList();
        List<String> combinations = new ArrayList<>();
        for (int first : pools.get(0)) {
            for (int second : pools.get(1)) {
                for (int third : pools.get(2)) {
                    if (first + second + third == 6) combinations.add(first + ":" + second + ":" + third);
                }
            }
        }
        if (combinations.isEmpty()) {
            unavailable(joint, "各区候选无法组成六个红球；不自动补0或修改个数。");
            return;
        }
        joint.setStatus("OK");
        joint.setFailureReason(null);
        joint.setLikelyValues(List.copyOf(combinations));
        joint.setLikelyReason("由一区、二区、三区的候选个数交叉组合，仅保留合计6的组合："
            + JSON.toJSONString(combinations) + "。0仅来自该区原有候选，不自动补0。三区比不是独立模型预测。");
        render(joint);
        for (int index = 0; index < counts.size(); index++) {
            var item = counts.get(index);
            final int component = index;
            var preferred = combinations.stream().map(value -> value.split(":")[component]).distinct()
                .sorted(java.util.Comparator.comparingInt(Integer::parseInt)).toList();
            var removed = item.getLikelyValues().stream().filter(value -> !preferred.contains(value)).toList();
            String originalReason = Objects.toString(item.getLikelyReason(), "模型未提供原始理由");
            item.setLikelyValues(preferred);
            item.setLikelyReason("原始各区分析依据：" + originalReason + "；最终候选由有效三区组合投影得到："
                + JSON.toJSONString(combinations) + "。原候选" + JSON.toJSONString(removed)
                + "缺少联合组合支持，未自动归入暂不优先。");
            render(item);
        }
    }

    private static PatternFeatureAnalysisBo find(List<PatternFeatureAnalysisBo> results, String code) {
        return results.stream().filter(Objects::nonNull).filter(item -> code.equals(item.getCode()))
            .findFirst().orElse(null);
    }

    private static void unavailable(PatternFeatureAnalysisBo joint, String reason) {
        joint.setStatus("UNAVAILABLE");
        joint.setFailureReason(reason);
        joint.setLikelyReason(reason);
        render(joint);
    }

    private static void render(PatternFeatureAnalysisBo item) {
        item.setNextPeriodInference("倾向出现的" + item.getLabel() + "池：" + JSON.toJSONString(item.getLikelyValues())
            + "\n理由：" + item.getLikelyReason() + "\n暂不优先的" + item.getLabel() + "池："
            + JSON.toJSONString(item.getUnlikelyValues()) + "\n理由：" + item.getUnlikelyReason());
    }
}
