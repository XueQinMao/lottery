package com.my.project.service.enums;

import com.my.project.service.support.LotteryPatternTrendUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.IntPredicate;

/**
 * FeatureKindEnums
 *
 * @author 刘强
 * @version 2026/08/26 19:54
 **/
@Getter
@AllArgsConstructor
public enum FeatureKindEnums {

    ODD_EVEN("oddEven", "奇偶比", false, List.of("0:6", "1:5", "2:4", "3:3", "4:2", "5:1", "6:0"),
        FeatureKindEnums::oddEven),
    BIG_SMALL("bigSmall", "大小比", false, List.of("0:6", "1:5", "2:4", "3:3", "4:2", "5:1", "6:0"),
        FeatureKindEnums::bigSmall),
    PRIME_COMP("primeComp", "质合比", false, List.of("0:6", "1:5", "2:4", "3:3", "4:2", "5:1", "6:0"),
        FeatureKindEnums::primeComp),
    RATIO_012("ratio012", "012路比", false, List.of(
        "0:0:6", "0:1:5", "0:2:4", "0:3:3", "0:4:2", "0:5:1", "0:6:0",
        "1:0:5", "1:1:4", "1:2:3", "1:3:2", "1:4:1", "1:5:0",
        "2:0:4", "2:1:3", "2:2:2", "2:3:1", "2:4:0",
        "3:0:3", "3:1:2", "3:2:1", "3:3:0",
        "4:0:2", "4:1:1", "4:2:0",
        "5:0:1", "5:1:0",
        "6:0:0"), FeatureKindEnums::ratio012),
    SPAN("span", "跨度", false, List.of(
        "5", "6", "7", "8", "9", "10", "11", "12", "13", "14",
        "15", "16", "17", "18", "19", "20", "21", "22", "23", "24",
        "25", "26", "27", "28", "29", "30", "31", "32"), FeatureKindEnums::span),
    SUM_RANGE("sumRange", "和值区间", false, List.of(
        "21-60", "61-66", "67-72", "73-78", "79-84", "85-90", "91-96", "97-102",
        "103-108", "109-114", "115-120", "121-126", "127-132", "133-138", "139-144", "145-183"),
        FeatureKindEnums::sumRange),
    SUM_TAIL("sumTail", "和值尾数", false, List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9"),
        FeatureKindEnums::sumTail),
    THREE_ZONE("threeZone", "三区比", false, List.of(
        "0:0:6", "0:1:5", "0:2:4", "0:3:3", "0:4:2", "0:5:1", "0:6:0",
        "1:0:5", "1:1:4", "1:2:3", "1:3:2", "1:4:1", "1:5:0",
        "2:0:4", "2:1:3", "2:2:2", "2:3:1", "2:4:0",
        "3:0:3", "3:1:2", "3:2:1", "3:3:0",
        "4:0:2", "4:1:1", "4:2:0",
        "5:0:1", "5:1:0",
        "6:0:0"), FeatureKindEnums::threeZone),
    ZONE1_COUNT("zone1Count", "一区个数", false, List.of("0", "1", "2", "3", "4", "5", "6"),
        FeatureKindEnums::zone1Count),
    ZONE2_COUNT("zone2Count", "二区个数", false, List.of("0", "1", "2", "3", "4", "5", "6"),
        FeatureKindEnums::zone2Count),
    ZONE3_COUNT("zone3Count", "三区个数", false, List.of("0", "1", "2", "3", "4", "5", "6"),
        FeatureKindEnums::zone3Count),
    BLUE_ODD_EVEN("blueOddEven", "蓝球奇偶", true, List.of("奇", "偶"), FeatureKindEnums::blueOddEven),
    BLUE_BIG_SMALL("blueBigSmall", "蓝球大小", true, List.of("小", "大"), FeatureKindEnums::blueBigSmall),
    BLUE_BIG_SMALL_ODD_EVEN("blueBigSmallOddEven", "蓝球大小奇偶", true, List.of("小奇", "小偶", "大奇", "大偶"),
        FeatureKindEnums::blueBigSmallOddEven),
    BLUE_RATIO_012("blueRatio012", "蓝球012路", true, List.of("0路", "1路", "2路"), FeatureKindEnums::blueRatio012);

    private static final int RED_DRAW = LotteryPatternTrendUtils.RED_DRAW;

    private final String code;
    private final String label;
    private final boolean blue;
    private final List<String> vals;
    /** 入参：红球列表 + 蓝球，输出该形态对应特征值（如 3:3、21、奇） */
    private final BiFunction<List<Integer>, Integer, String> function;

    public String extract(List<Integer> reds, Integer blue) {
        return function.apply(reds, blue);
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

    public List<String> getVals() {
        return vals;
    }

    public BiFunction<List<Integer>, Integer, String> getFunction() {
        return function;
    }

    private static String oddEven(List<Integer> reds, Integer blue) {
        return twoRatio(reds, b -> b % 2 == 1);
    }

    private static String bigSmall(List<Integer> reds, Integer blue) {
        return twoRatio(reds, b -> b >= 17);
    }

    private static String primeComp(List<Integer> reds, Integer blue) {
        return twoRatio(reds, LotteryPatternTrendUtils.RED_PRIMES::contains);
    }

    private static String ratio012(List<Integer> reds, Integer blue) {
        int r0 = 0;
        int r1 = 0;
        int r2 = 0;
        if (reds != null) {
            for (Integer ball : reds) {
                if (ball == null) {
                    continue;
                }
                int mod = ball % 3;
                if (mod == 0) {
                    r0++;
                } else if (mod == 1) {
                    r1++;
                } else {
                    r2++;
                }
            }
        }
        return r0 + ":" + r1 + ":" + r2;
    }

    private static String span(List<Integer> reds, Integer blue) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        if (reds != null) {
            for (Integer ball : reds) {
                if (ball == null) {
                    continue;
                }
                if (ball < min) {
                    min = ball;
                }
                if (ball > max) {
                    max = ball;
                }
            }
        }
        return String.valueOf(max - min);
    }

    private static String sumRange(List<Integer> reds, Integer blue) {
        int sum = sumOf(reds);
        for (String range : SUM_RANGE.vals) {
            int dash = range.indexOf('-');
            int low = Integer.parseInt(range.substring(0, dash));
            int high = Integer.parseInt(range.substring(dash + 1));
            if (sum >= low && sum <= high) {
                return range;
            }
        }
        return SUM_RANGE.vals.get(0);
    }

    private static String sumTail(List<Integer> reds, Integer blue) {
        return String.valueOf(sumOf(reds) % 10);
    }

    private static String threeZone(List<Integer> reds, Integer blue) {
        int z1 = count(reds, b -> b <= 11);
        int z2 = count(reds, b -> b > 11 && b <= 22);
        int z3 = count(reds, b -> b > 22);
        return z1 + ":" + z2 + ":" + z3;
    }

    private static String zone1Count(List<Integer> reds, Integer blue) {
        return String.valueOf(count(reds, b -> b <= 11));
    }

    private static String zone2Count(List<Integer> reds, Integer blue) {
        return String.valueOf(count(reds, b -> b > 11 && b <= 22));
    }

    private static String zone3Count(List<Integer> reds, Integer blue) {
        return String.valueOf(count(reds, b -> b > 22));
    }

    private static String blueOddEven(List<Integer> reds, Integer blue) {
        return blueOf(blue) % 2 == 1 ? "奇" : "偶";
    }

    private static String blueBigSmall(List<Integer> reds, Integer blue) {
        return blueOf(blue) >= 9 ? "大" : "小";
    }

    private static String blueBigSmallOddEven(List<Integer> reds, Integer blue) {
        int b = blueOf(blue);
        return (b >= 9 ? "大" : "小") + (b % 2 == 1 ? "奇" : "偶");
    }

    private static String blueRatio012(List<Integer> reds, Integer blue) {
        int mod = blueOf(blue) % 3;
        return mod == 0 ? "0路" : (mod == 1 ? "1路" : "2路");
    }

    private static String twoRatio(List<Integer> reds, IntPredicate left) {
        int k = count(reds, left);
        return k + ":" + (RED_DRAW - k);
    }

    private static int count(List<Integer> reds, IntPredicate pred) {
        if (reds == null) {
            return 0;
        }
        int n = 0;
        for (Integer ball : reds) {
            if (ball != null && pred.test(ball)) {
                n++;
            }
        }
        return n;
    }

    private static int sumOf(List<Integer> reds) {
        if (reds == null) {
            return 0;
        }
        int sum = 0;
        for (Integer ball : reds) {
            if (ball != null) {
                sum += ball;
            }
        }
        return sum;
    }

    private static int blueOf(Integer blue) {
        return blue == null ? 0 : blue;
    }

}
