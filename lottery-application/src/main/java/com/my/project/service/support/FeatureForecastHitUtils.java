package com.my.project.service.support;

import com.my.project.llm.bo.FeatureForecastBo;
import com.my.project.llm.bo.FeatureForecastBo.FeatureForecastItem;
import com.my.project.llm.bo.LotteryAdjustRespBo;
import com.my.project.llm.bo.LotteryAdjustViewBo;
import com.my.project.llm.bo.LotteryAdjustViewBo.FeatureHit;
import com.my.project.llm.bo.LotteryAdjustViewBo.FeatureHitSummary;
import com.my.project.service.support.LotteryFeatureTrendUtils.FeatureKind;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * FeatureForecastHitUtils
 *
 * <p>将推荐单式（6 红 + 1 蓝）对照 {@link FeatureForecastBo}：命中主推、命中备选、未命中。
 *
 * @author 刘强
 * @version 2026/08/19
 **/
public final class FeatureForecastHitUtils {

    public static final String HIT_MAIN = "MAIN";
    public static final String HIT_ALT = "ALT";
    public static final String HIT_MISS = "MISS";

    private FeatureForecastHitUtils() {
    }

    public static LotteryAdjustViewBo toView(LotteryAdjustRespBo resp, FeatureForecastBo forecast) {
        LotteryAdjustViewBo view = new LotteryAdjustViewBo();
        if (resp == null) {
            return view;
        }
        view.setAdjustedTickets(resp.getAdjustedTickets());
        view.setFinalRecommendation(resp.getFinalRecommendation());
        view.setConclusion(resp.getConclusion());
        view.setFeatureForecast(forecast);

        List<FeatureHitSummary> adjustedHits =
            Optional.ofNullable(resp.getAdjustedTickets()).orElse(List.of()).stream()
                .map(ticket -> summarize(ticket.getAdjustedRedBalls(), ticket.getAdjustedBlueBall(), forecast))
                .toList();
        view.setAdjustedTicketHits(adjustedHits);

        List<FeatureHitSummary> finalHits =
            Optional.ofNullable(resp.getFinalRecommendation())
                .map(r -> Optional.ofNullable(r.getSingleTickets()).orElse(List.of()))
                .orElse(List.of())
                .stream()
                .map(ticket -> summarize(ticket.getRedBalls(), ticket.getBlueBall(), forecast))
                .toList();
        view.setFinalSingleHits(finalHits);
        return view;
    }

    public static FeatureHitSummary summarize(List<Integer> reds, Integer blue, FeatureForecastBo forecast) {
        FeatureHitSummary summary = new FeatureHitSummary();
        List<FeatureHit> hits = analyze(reds, blue, forecast);
        summary.setHits(hits);
        summary.setMainHitCount((int) hits.stream().filter(h -> HIT_MAIN.equals(h.getHitType())).count());
        summary.setAltHitCount((int) hits.stream().filter(h -> HIT_ALT.equals(h.getHitType())).count());
        summary.setMissCount((int) hits.stream().filter(h -> HIT_MISS.equals(h.getHitType())).count());
        return summary;
    }

    public static List<FeatureHit> analyze(List<Integer> reds, Integer blue, FeatureForecastBo forecast) {
        if (forecast == null || CollectionUtils.size(reds) != 6 || blue == null) {
            return List.of();
        }
        return Arrays.stream(FeatureKind.values())
            .map(kind -> {
                FeatureForecastItem item = forecast.itemOf(kind.getCode());
                String actual;
                try {
                    actual = LotteryFeatureTrendUtils.extract(reds, blue, kind);
                } catch (Exception e) {
                    actual = null;
                }
                FeatureHit hit = new FeatureHit();
                hit.setCode(kind.getCode());
                hit.setLabel(kind.getLabel());
                hit.setActual(actual);
                if (item != null) {
                    hit.setMainValue(item.getValue());
                    hit.setAlternatives(item.getAlternatives());
                }
                hit.setHitType(resolveHitType(actual, item));
                return hit;
            })
            .toList();
    }

    static String resolveHitType(String actual, FeatureForecastItem item) {
        if (actual == null || item == null) {
            return HIT_MISS;
        }
        if (matches(item.getValue(), actual)) {
            return HIT_MAIN;
        }
        if (item.getAlternatives() != null
            && item.getAlternatives().stream().anyMatch(alt -> matches(alt, actual))) {
            return HIT_ALT;
        }
        return HIT_MISS;
    }

    /**
     * 精确相等，或目标为闭区间且实际值（或实际区间）落在其中。
     * <p>覆盖跨度/尾数/区个数的「20-24 vs 21」，以及和值区间的「91-108 vs 97-102」。
     */
    public static boolean matches(String target, String actual) {
        if (target == null || actual == null) {
            return false;
        }
        String t = target.trim();
        String a = actual.trim();
        if (t.isEmpty() || a.isEmpty()) {
            return false;
        }
        if (t.equals(a)) {
            return true;
        }
        int[] tr = parseRange(t);
        int[] ar = parseRange(a);
        if (tr != null && ar != null) {
            return ar[0] >= tr[0] && ar[1] <= tr[1];
        }
        if (tr != null) {
            try {
                int v = Integer.parseInt(a);
                return v >= tr[0] && v <= tr[1];
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }

    private static int[] parseRange(String s) {
        if (s == null || !s.matches("\\d+-\\d+")) {
            return null;
        }
        int dash = s.indexOf('-');
        try {
            int lo = Integer.parseInt(s.substring(0, dash));
            int hi = Integer.parseInt(s.substring(dash + 1));
            return new int[] {Math.min(lo, hi), Math.max(lo, hi)};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
