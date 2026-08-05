package com.my.project.service.support;

import java.util.ArrayList;
import java.util.List;

/**
 * LotteryFeatureTrendUtils
 *
 * <p>红球形态分桶与理论概率：012 路、跨度、和值区间、和值尾数、三区比、一/二/三区个数，
 * 以及原有的奇偶比 / 大小比 / 质合比。
 * <p>蓝球（1-16，每期 1 个）形态：奇偶、大小（1-8 小 / 9-16 大）、大小奇偶、012 路。
 * 遗漏与超额指数仍由 {@link LotteryPatternTrendUtils#analyze} 计算。
 *
 * @author 刘强
 * @version 2026/08/14
 **/
public final class LotteryFeatureTrendUtils {

    private static final int RED_TOTAL = LotteryPatternTrendUtils.RED_TOTAL;
    private static final int RED_DRAW = LotteryPatternTrendUtils.RED_DRAW;
    private static final long C_33_6 = LotteryPatternTrendUtils.C_33_6;
    /** 蓝球 1-16 */
    public static final int BLUE_TOTAL = 16;

    /** 0 路 / 1 路 / 2 路 / 一区 / 二区 / 三区 均为 11 个号 */
    public static final int ROAD_POOL = 11;
    public static final int ZONE_POOL = 11;
    public static final int ZONE_OTHER = 22;

    public static final int SPAN_MIN = 5;
    public static final int SPAN_MAX = 32;

    /** 和值区间 [low, high]，含端点 */
    public static final int[][] SUM_RANGES = {
        {21, 60}, {61, 66}, {67, 72}, {73, 78}, {79, 84}, {85, 90}, {91, 96}, {97, 102},
        {103, 108}, {109, 114}, {115, 120}, {121, 126}, {127, 132}, {133, 138}, {139, 144}, {145, 183}
    };

    private static final int[] SUM_WAYS = new int[184];
    private static final int[] SUM_TAIL_WAYS = new int[10];

    static {
        enumerateSums(1, RED_DRAW, 0);
        int total = 0;
        for (int w : SUM_WAYS) {
            total += w;
        }
        if (total != C_33_6) {
            throw new IllegalStateException("和值组合枚举与 C(33,6) 不一致: " + total);
        }
    }

    private LotteryFeatureTrendUtils() {
    }

    public enum FeatureKind {
        ODD_EVEN("oddEven", "奇偶比", false),
        BIG_SMALL("bigSmall", "大小比", false),
        PRIME_COMP("primeComp", "质合比", false),
        RATIO_012("ratio012", "012路比", false),
        SPAN("span", "跨度", false),
        SUM_RANGE("sumRange", "和值区间", false),
        SUM_TAIL("sumTail", "和值尾数", false),
        THREE_ZONE("threeZone", "三区比", false),
        ZONE1_COUNT("zone1Count", "一区个数", false),
        ZONE2_COUNT("zone2Count", "二区个数", false),
        ZONE3_COUNT("zone3Count", "三区个数", false),
        BLUE_ODD_EVEN("blueOddEven", "蓝球奇偶", true),
        BLUE_BIG_SMALL("blueBigSmall", "蓝球大小", true),
        BLUE_BIG_SMALL_ODD_EVEN("blueBigSmallOddEven", "蓝球大小奇偶", true),
        BLUE_RATIO_012("blueRatio012", "蓝球012路", true);

        private final String code;
        private final String label;
        private final boolean blue;

        FeatureKind(String code, String label, boolean blue) {
            this.code = code;
            this.label = label;
            this.blue = blue;
        }

        public String getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }

        public boolean isBlue() {
            return blue;
        }

        /** 给 LLM 的取值格式说明 */
        public String valueHint() {
            return switch (this) {
                case ODD_EVEN, BIG_SMALL -> "精确比例，如 3:3，左右之和必须为 6";
                case PRIME_COMP -> "精确比例，如 2:4，左右之和必须为 6；01 计为质数";
                case RATIO_012, THREE_ZONE -> "精确三元比例，如 2:2:2，三项之和必须为 6";
                case SPAN -> "精确值如 21，或闭区间如 20-24，范围 5-32";
                case SUM_RANGE -> "和值区间如 97-102，或合并如 91-108";
                case SUM_TAIL -> "尾数 0-9 的精确值或闭区间如 2-5";
                case ZONE1_COUNT, ZONE2_COUNT, ZONE3_COUNT -> "个数 0-6 的精确值或闭区间如 1-2";
                case BLUE_ODD_EVEN -> "精确值：奇 或 偶（蓝球 1-16 各 8 个，p=0.5）";
                case BLUE_BIG_SMALL -> "精确值：大 或 小（小=1-8，大=9-16，各 8 个，p=0.5）";
                case BLUE_BIG_SMALL_ODD_EVEN -> "精确值：小奇、小偶、大奇、大偶（各 4 个，p=0.25）";
                case BLUE_RATIO_012 -> "精确值：0路、1路、2路（0路 5 个 p=5/16，1路 6 个 p=6/16，2路 5 个 p=5/16）";
            };
        }

        public static FeatureKind fromCode(String code) {
            if (code == null || code.isBlank()) {
                return ODD_EVEN;
            }
            String normalized = code.trim();
            for (FeatureKind kind : values()) {
                if (kind.code.equalsIgnoreCase(normalized) || kind.name().equalsIgnoreCase(normalized)) {
                    return kind;
                }
            }
            throw new IllegalArgumentException(
                "feature 仅支持 oddEven/bigSmall/primeComp/ratio012/span/sumRange/sumTail/threeZone/"
                    + "zone1Count/zone2Count/zone3Count/blueOddEven/blueBigSmall/blueBigSmallOddEven/blueRatio012");
        }
    }

    public static List<String> buckets(FeatureKind kind) {
        List<String> list = new ArrayList<>();
        switch (kind) {
            case ODD_EVEN, BIG_SMALL, PRIME_COMP -> {
                for (int k = 0; k <= RED_DRAW; k++) {
                    list.add(k + ":" + (RED_DRAW - k));
                }
            }
            case RATIO_012, THREE_ZONE -> {
                for (int a = 0; a <= RED_DRAW; a++) {
                    for (int b = 0; b <= RED_DRAW - a; b++) {
                        list.add(a + ":" + b + ":" + (RED_DRAW - a - b));
                    }
                }
            }
            case SPAN -> {
                for (int s = SPAN_MIN; s <= SPAN_MAX; s++) {
                    list.add(String.valueOf(s));
                }
            }
            case SUM_RANGE -> {
                for (int[] range : SUM_RANGES) {
                    list.add(range[0] + "-" + range[1]);
                }
            }
            case SUM_TAIL -> {
                for (int d = 0; d <= 9; d++) {
                    list.add(String.valueOf(d));
                }
            }
            case ZONE1_COUNT, ZONE2_COUNT, ZONE3_COUNT -> {
                for (int k = 0; k <= RED_DRAW; k++) {
                    list.add(String.valueOf(k));
                }
            }
            case BLUE_ODD_EVEN -> {
                list.add("奇");
                list.add("偶");
            }
            case BLUE_BIG_SMALL -> {
                list.add("小");
                list.add("大");
            }
            case BLUE_BIG_SMALL_ODD_EVEN -> {
                list.add("小奇");
                list.add("小偶");
                list.add("大奇");
                list.add("大偶");
            }
            case BLUE_RATIO_012 -> {
                list.add("0路");
                list.add("1路");
                list.add("2路");
            }
        }
        return list;
    }

    public static String defaultBucket(FeatureKind kind) {
        return switch (kind) {
            case ODD_EVEN, BIG_SMALL -> "3:3";
            case PRIME_COMP -> "2:4";
            case RATIO_012 -> "0:4:2";
            case SPAN -> "21";
            case SUM_RANGE -> "73-78";
            case SUM_TAIL -> "0";
            case THREE_ZONE -> "1:1:4";
            case ZONE1_COUNT, ZONE2_COUNT -> "2";
            case ZONE3_COUNT -> "1";
            case BLUE_ODD_EVEN -> "奇";
            case BLUE_BIG_SMALL -> "大";
            case BLUE_BIG_SMALL_ODD_EVEN -> "大奇";
            case BLUE_RATIO_012 -> "0路";
        };
    }

    public static String normalizeBucket(FeatureKind kind, String raw) {
        if (raw == null || raw.isBlank()) {
            return defaultBucket(kind);
        }
        String s = raw.trim().replace('：', ':').replace('—', '-').replace('～', '-');
        return switch (kind) {
            case ODD_EVEN, BIG_SMALL, PRIME_COMP -> {
                int left = LotteryPatternTrendUtils.parseRatioLeft(s);
                yield left + ":" + (RED_DRAW - left);
            }
            case RATIO_012, THREE_ZONE -> {
                int[] t = parseTriple(s);
                yield t[0] + ":" + t[1] + ":" + t[2];
            }
            case SPAN -> {
                int span = parseIntBucket(s, "跨度");
                if (span < SPAN_MIN || span > SPAN_MAX) {
                    throw new IllegalArgumentException("跨度范围为 " + SPAN_MIN + "-" + SPAN_MAX);
                }
                yield String.valueOf(span);
            }
            case SUM_RANGE -> {
                String bucket = sumRangeOf(parseSumRangeLow(s));
                if (bucket == null) {
                    throw new IllegalArgumentException("和值区间格式如 73-78");
                }
                yield bucket;
            }
            case SUM_TAIL -> {
                int d = parseIntBucket(s, "和值尾数");
                if (d < 0 || d > 9) {
                    throw new IllegalArgumentException("和值尾数范围为 0-9");
                }
                yield String.valueOf(d);
            }
            case ZONE1_COUNT, ZONE2_COUNT, ZONE3_COUNT -> {
                int k = parseIntBucket(s.replace("个", ""), "区个数");
                if (k < 0 || k > RED_DRAW) {
                    throw new IllegalArgumentException("区个数范围为 0-6");
                }
                yield String.valueOf(k);
            }
            case BLUE_ODD_EVEN -> {
                if (s.contains("奇") || "odd".equalsIgnoreCase(s)) {
                    yield "奇";
                }
                if (s.contains("偶") || "even".equalsIgnoreCase(s)) {
                    yield "偶";
                }
                throw new IllegalArgumentException("蓝球奇偶仅支持 奇/偶");
            }
            case BLUE_BIG_SMALL -> {
                if (s.contains("大") || "big".equalsIgnoreCase(s)) {
                    yield "大";
                }
                if (s.contains("小") || "small".equalsIgnoreCase(s)) {
                    yield "小";
                }
                throw new IllegalArgumentException("蓝球大小仅支持 大/小");
            }
            case BLUE_BIG_SMALL_ODD_EVEN -> {
                String compact = s.replace("数", "").replace("号", "");
                if (compact.contains("小奇")) {
                    yield "小奇";
                }
                if (compact.contains("小偶")) {
                    yield "小偶";
                }
                if (compact.contains("大奇")) {
                    yield "大奇";
                }
                if (compact.contains("大偶")) {
                    yield "大偶";
                }
                throw new IllegalArgumentException("蓝球大小奇偶仅支持 小奇/小偶/大奇/大偶");
            }
            case BLUE_RATIO_012 -> {
                if (s.startsWith("0") || s.contains("0路")) {
                    yield "0路";
                }
                if (s.startsWith("1") || s.contains("1路")) {
                    yield "1路";
                }
                if (s.startsWith("2") || s.contains("2路")) {
                    yield "2路";
                }
                throw new IllegalArgumentException("蓝球012路仅支持 0路/1路/2路");
            }
        };
    }

    public static String extract(List<Integer> reds, Integer blue, FeatureKind kind) {
        if (kind.isBlue()) {
            return extractBlue(blue == null ? 0 : blue, kind);
        }
        return extract(reds, kind);
    }

    public static String extractBlue(int blue, FeatureKind kind) {
        if (!kind.isBlue()) {
            throw new IllegalArgumentException(kind.getLabel() + " 不是蓝球形态");
        }
        boolean odd = blue % 2 == 1;
        boolean big = blue >= 9;
        int mod = blue % 3;
        return switch (kind) {
            case BLUE_ODD_EVEN -> odd ? "奇" : "偶";
            case BLUE_BIG_SMALL -> big ? "大" : "小";
            case BLUE_BIG_SMALL_ODD_EVEN -> (big ? "大" : "小") + (odd ? "奇" : "偶");
            case BLUE_RATIO_012 -> mod == 0 ? "0路" : (mod == 1 ? "1路" : "2路");
            default -> throw new IllegalArgumentException(kind.getLabel() + " 不是蓝球形态");
        };
    }

    public static String extract(List<Integer> reds, FeatureKind kind) {
        if (kind.isBlue()) {
            throw new IllegalArgumentException(kind.getLabel() + " 需传入蓝球，请使用 extract(reds, blue, kind)");
        }
        int odd = 0;
        int big = 0;
        int prime = 0;
        int r0 = 0;
        int r1 = 0;
        int r2 = 0;
        int z1 = 0;
        int z2 = 0;
        int z3 = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int sum = 0;
        for (Integer ball : reds) {
            if (ball == null) {
                continue;
            }
            int b = ball;
            sum += b;
            if (b < min) {
                min = b;
            }
            if (b > max) {
                max = b;
            }
            if (b % 2 == 1) {
                odd++;
            }
            if (b >= 17) {
                big++;
            }
            if (LotteryPatternTrendUtils.RED_PRIMES.contains(b)) {
                prime++;
            }
            int mod = b % 3;
            if (mod == 0) {
                r0++;
            } else if (mod == 1) {
                r1++;
            } else {
                r2++;
            }
            if (b <= 11) {
                z1++;
            } else if (b <= 22) {
                z2++;
            } else {
                z3++;
            }
        }
        return switch (kind) {
            case ODD_EVEN -> odd + ":" + (RED_DRAW - odd);
            case BIG_SMALL -> big + ":" + (RED_DRAW - big);
            case PRIME_COMP -> prime + ":" + (RED_DRAW - prime);
            case RATIO_012 -> r0 + ":" + r1 + ":" + r2;
            case SPAN -> String.valueOf(max - min);
            case SUM_RANGE -> sumRangeOf(sum);
            case SUM_TAIL -> String.valueOf(sum % 10);
            case THREE_ZONE -> z1 + ":" + z2 + ":" + z3;
            case ZONE1_COUNT -> String.valueOf(z1);
            case ZONE2_COUNT -> String.valueOf(z2);
            case ZONE3_COUNT -> String.valueOf(z3);
            case BLUE_ODD_EVEN, BLUE_BIG_SMALL, BLUE_BIG_SMALL_ODD_EVEN, BLUE_RATIO_012 ->
                throw new IllegalStateException("蓝球形态应走 extractBlue");
        };
    }

    public static double theoreticalProb(FeatureKind kind, String bucket) {
        String key = normalizeBucket(kind, bucket);
        if (kind.isBlue()) {
            return blueTheoreticalProb(kind, key);
        }
        long ways = switch (kind) {
            case ODD_EVEN -> twoPoolWays(LotteryPatternTrendUtils.ODD_POOL, LotteryPatternTrendUtils.EVEN_POOL, key);
            case BIG_SMALL -> twoPoolWays(LotteryPatternTrendUtils.BIG_POOL, LotteryPatternTrendUtils.SMALL_POOL, key);
            case PRIME_COMP -> twoPoolWays(LotteryPatternTrendUtils.PRIME_POOL, LotteryPatternTrendUtils.COMPOSITE_POOL, key);
            case RATIO_012, THREE_ZONE -> {
                int[] t = parseTriple(key);
                yield LotteryPatternTrendUtils.combination(ROAD_POOL, t[0])
                    * LotteryPatternTrendUtils.combination(ROAD_POOL, t[1])
                    * LotteryPatternTrendUtils.combination(ROAD_POOL, t[2]);
            }
            case SPAN -> {
                int span = Integer.parseInt(key);
                yield (long) (RED_TOTAL - span) * LotteryPatternTrendUtils.combination(span - 1, 4);
            }
            case SUM_RANGE -> sumRangeWays(key);
            case SUM_TAIL -> SUM_TAIL_WAYS[Integer.parseInt(key)];
            case ZONE1_COUNT, ZONE2_COUNT, ZONE3_COUNT -> {
                int k = Integer.parseInt(key);
                yield LotteryPatternTrendUtils.combination(ZONE_POOL, k)
                    * LotteryPatternTrendUtils.combination(ZONE_OTHER, RED_DRAW - k);
            }
            case BLUE_ODD_EVEN, BLUE_BIG_SMALL, BLUE_BIG_SMALL_ODD_EVEN, BLUE_RATIO_012 ->
                throw new IllegalStateException("蓝球形态应走 blueTheoreticalProb");
        };
        return (double) ways / C_33_6;
    }

    private static double blueTheoreticalProb(FeatureKind kind, String key) {
        int ways = switch (kind) {
            case BLUE_ODD_EVEN, BLUE_BIG_SMALL -> 8;
            case BLUE_BIG_SMALL_ODD_EVEN -> 4;
            case BLUE_RATIO_012 -> "1路".equals(key) ? 6 : 5;
            default -> throw new IllegalArgumentException(kind.getLabel() + " 不是蓝球形态");
        };
        return (double) ways / BLUE_TOTAL;
    }

    private static long twoPoolWays(int poolA, int poolB, String ratio) {
        int left = LotteryPatternTrendUtils.parseRatioLeft(ratio);
        int right = RED_DRAW - left;
        return LotteryPatternTrendUtils.combination(poolA, left) * LotteryPatternTrendUtils.combination(poolB, right);
    }

    private static long sumRangeWays(String bucket) {
        int dash = bucket.indexOf('-');
        int low = Integer.parseInt(bucket.substring(0, dash));
        int high = Integer.parseInt(bucket.substring(dash + 1));
        long ways = 0;
        for (int s = low; s <= high && s < SUM_WAYS.length; s++) {
            ways += SUM_WAYS[s];
        }
        return ways;
    }

    private static String sumRangeOf(int sum) {
        for (int[] range : SUM_RANGES) {
            if (sum >= range[0] && sum <= range[1]) {
                return range[0] + "-" + range[1];
            }
        }
        return SUM_RANGES[0][0] + "-" + SUM_RANGES[0][1];
    }

    private static int parseSumRangeLow(String s) {
        if (s.matches("\\d{2,3}-\\d{2,3}")) {
            return Integer.parseInt(s.substring(0, s.indexOf('-')));
        }
        if (s.matches("\\d{2,3}")) {
            return Integer.parseInt(s);
        }
        throw new IllegalArgumentException("和值区间格式如 73-78");
    }

    private static int[] parseTriple(String raw) {
        String s = raw.replace(" ", "");
        int a;
        int b;
        int c;
        if (s.matches("\\d:\\d:\\d")) {
            String[] p = s.split(":");
            a = Integer.parseInt(p[0]);
            b = Integer.parseInt(p[1]);
            c = Integer.parseInt(p[2]);
        } else if (s.matches("\\d{3}")) {
            a = s.charAt(0) - '0';
            b = s.charAt(1) - '0';
            c = s.charAt(2) - '0';
        } else {
            throw new IllegalArgumentException("三元比例格式如 0:4:2 或 042");
        }
        if (a < 0 || b < 0 || c < 0 || a + b + c != RED_DRAW) {
            throw new IllegalArgumentException("三元比例之和必须为 6");
        }
        return new int[] {a, b, c};
    }

    private static int parseIntBucket(String s, String name) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " 必须为整数");
        }
    }

    private static void enumerateSums(int start, int remain, int sum) {
        if (remain == 0) {
            SUM_WAYS[sum]++;
            SUM_TAIL_WAYS[sum % 10]++;
            return;
        }
        for (int i = start; i <= RED_TOTAL - remain + 1; i++) {
            enumerateSums(i + 1, remain - 1, sum + i);
        }
    }
}
