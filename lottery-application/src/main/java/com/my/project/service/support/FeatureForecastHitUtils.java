package com.my.project.service.support;

import com.my.project.llm.bo.FeatureForecastBo;
import com.my.project.llm.bo.FeatureForecastBo.FeatureForecastItem;
import com.my.project.llm.bo.LotteryAdjustRespBo;
import com.my.project.llm.bo.LotteryAdjustViewBo;
import com.my.project.llm.bo.LotteryAdjustViewBo.FeatureHit;
import com.my.project.llm.bo.LotteryAdjustViewBo.FeatureHitSummary;
import com.my.project.service.support.LotteryFeatureTrendUtils.FeatureKind;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

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

        List<FeatureHitSummary> adjustedHits = new ArrayList<>();
        if (resp.getAdjustedTickets() != null) {
            for (var ticket : resp.getAdjustedTickets()) {
                adjustedHits.add(summarize(ticket.getAdjustedRedBalls(), ticket.getAdjustedBlueBall(), forecast));
            }
        }
        view.setAdjustedTicketHits(adjustedHits);

        List<FeatureHitSummary> finalHits = new ArrayList<>();
        if (resp.getFinalRecommendation() != null
            && resp.getFinalRecommendation().getSingleTickets() != null) {
            for (var ticket : resp.getFinalRecommendation().getSingleTickets()) {
                finalHits.add(summarize(ticket.getRedBalls(), ticket.getBlueBall(), forecast));
            }
        }
        view.setFinalSingleHits(finalHits);
        return view;
    }

    public static FeatureHitSummary summarize(List<Integer> reds, Integer blue, FeatureForecastBo forecast) {
        FeatureHitSummary summary = new FeatureHitSummary();
        List<FeatureHit> hits = analyze(reds, blue, forecast);
        summary.setHits(hits);
        int main = 0;
        int alt = 0;
        int miss = 0;
        for (FeatureHit hit : hits) {
            if (HIT_MAIN.equals(hit.getHitType())) {
                main++;
            } else if (HIT_ALT.equals(hit.getHitType())) {
                alt++;
            } else {
                miss++;
            }
        }
        summary.setMainHitCount(main);
        summary.setAltHitCount(alt);
        summary.setMissCount(miss);
        return summary;
    }

    public static List<FeatureHit> analyze(List<Integer> reds, Integer blue, FeatureForecastBo forecast) {
        List<FeatureHit> hits = new ArrayList<>();
        if (forecast == null || CollectionUtils.size(reds) != 6 || blue == null) {
            return hits;
        }
        for (FeatureKind kind : FeatureKind.values()) {
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
            hits.add(hit);
        }
        return hits;
    }

    static String resolveHitType(String actual, FeatureForecastItem item) {
        if (actual == null || item == null) {
            return HIT_MISS;
        }
        if (matches(item.getValue(), actual)) {
            return HIT_MAIN;
        }
        if (item.getAlternatives() != null) {
            for (String alt : item.getAlternatives()) {
                if (matches(alt, actual)) {
                    return HIT_ALT;
                }
            }
        }
        return HIT_MISS;
    }

    /**
     * 精确相等，或目标为闭区间且实际值（或实际区间）落在其中。
     * <p>覆盖跨度/尾数/区个数的「20-24 vs 21」，以及和值区间的「91-108 vs 97-102」。
     */
    static boolean matches(String target, String actual) {
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
