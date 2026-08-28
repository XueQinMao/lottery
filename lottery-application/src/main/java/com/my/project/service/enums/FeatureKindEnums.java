package com.my.project.service.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * FeatureKindEnums
 *
 * @author 刘强
 * @version 2026/08/26 19:54
 **/
@Getter
@AllArgsConstructor
public enum FeatureKindEnums {

    ODD_EVEN("oddEven", "奇偶比", false, List.of("0:6", "1:5", "2:4", "3:3", "4:2", "5:1", "6:0")),
    BIG_SMALL("bigSmall", "大小比", false, List.of("0:6", "1:5", "2:4", "3:3", "4:2", "5:1", "6:0")),
    PRIME_COMP("primeComp", "质合比", false, List.of("0:6", "1:5", "2:4", "3:3", "4:2", "5:1", "6:0")),
    RATIO_012("ratio012", "012路比", false, List.of(
        "0:0:6", "0:1:5", "0:2:4", "0:3:3", "0:4:2", "0:5:1", "0:6:0",
        "1:0:5", "1:1:4", "1:2:3", "1:3:2", "1:4:1", "1:5:0",
        "2:0:4", "2:1:3", "2:2:2", "2:3:1", "2:4:0",
        "3:0:3", "3:1:2", "3:2:1", "3:3:0",
        "4:0:2", "4:1:1", "4:2:0",
        "5:0:1", "5:1:0",
        "6:0:0")),
    SPAN("span", "跨度", false, List.of(
        "5", "6", "7", "8", "9", "10", "11", "12", "13", "14",
        "15", "16", "17", "18", "19", "20", "21", "22", "23", "24",
        "25", "26", "27", "28", "29", "30", "31", "32")),
    SUM_RANGE("sumRange", "和值区间", false, List.of(
        "21-60", "61-66", "67-72", "73-78", "79-84", "85-90", "91-96", "97-102",
        "103-108", "109-114", "115-120", "121-126", "127-132", "133-138", "139-144", "145-183")),
    SUM_TAIL("sumTail", "和值尾数", false, List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")),
    THREE_ZONE("threeZone", "三区比", false, List.of(
        "0:0:6", "0:1:5", "0:2:4", "0:3:3", "0:4:2", "0:5:1", "0:6:0",
        "1:0:5", "1:1:4", "1:2:3", "1:3:2", "1:4:1", "1:5:0",
        "2:0:4", "2:1:3", "2:2:2", "2:3:1", "2:4:0",
        "3:0:3", "3:1:2", "3:2:1", "3:3:0",
        "4:0:2", "4:1:1", "4:2:0",
        "5:0:1", "5:1:0",
        "6:0:0")),
    ZONE1_COUNT("zone1Count", "一区个数", false, List.of("0", "1", "2", "3", "4", "5", "6")),
    ZONE2_COUNT("zone2Count", "二区个数", false, List.of("0", "1", "2", "3", "4", "5", "6")),
    ZONE3_COUNT("zone3Count", "三区个数", false, List.of("0", "1", "2", "3", "4", "5", "6")),
    BLUE_ODD_EVEN("blueOddEven", "蓝球奇偶", true, List.of("奇", "偶")),
    BLUE_BIG_SMALL("blueBigSmall", "蓝球大小", true, List.of("小", "大")),
    BLUE_BIG_SMALL_ODD_EVEN("blueBigSmallOddEven", "蓝球大小奇偶", true, List.of("小奇", "小偶", "大奇", "大偶")),
    BLUE_RATIO_012("blueRatio012", "蓝球012路", true, List.of("0路", "1路", "2路"));

    private final String code;
    private final String label;
    private final boolean blue;
    private final List<String> vals;

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
}
