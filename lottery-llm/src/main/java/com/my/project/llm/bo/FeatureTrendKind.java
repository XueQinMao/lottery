package com.my.project.llm.bo;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public enum FeatureTrendKind {
    RED_ZONES("redZones", "三区比", false, "zones threeZone", "一区:二区:三区，合计6", Shape.TRIPLE),
    RED_TEMPERATURE("redTemperature", "三区冷温热", false, "coldHot zone1ColdHot zone2ColdHot zone3ColdHot",
        "格式为 总体|一区|二区|三区，每组按冷:温:热。总体合计6，各区同一档相加必须等于总体同档", Shape.TEMPERATURE),
    RED_PARITY("redParity", "奇偶比", false, "oddEven oddEvenRatio", "奇:偶，合计6", Shape.PAIR),
    RED_ROAD("redRoad", "012路比", false, "road roadRatio", "0路:1路:2路，合计6", Shape.TRIPLE),
    RED_SUM("redSum", "和值", false, "sum sumDelta", "六个红球和值，返回21到183的整数", Shape.SUM),
    RED_SPAN("redSpan", "跨度", false, "span", "最大红球减最小红球，返回5到32的整数", Shape.SPAN),
    RED_SIZE("redSize", "大小个数", false, "size", "小:大，合计6，小为1–16，大为17–33", Shape.PAIR),
    RED_PRIME("redPrime", "质合比", false, "primeRatio", "质:合，合计6，沿用图表口径，1归入合数档", Shape.PAIR),
    RED_HEAD("redHead", "龙头012路", false, "headRoad", "每个候选只填一个路数（0路、1路或2路），龙头是最小红球", Shape.ROAD),
    RED_TAIL("redTail", "凤尾012路", false, "tailRoad", "每个候选只填一个路数（0路、1路或2路），凤尾是最大红球", Shape.ROAD),
    RED_ENDINGS("redEndings", "尾数分布", false, "tailCounts",
        "从0尾到9尾依次填写十个个数，用冒号连接，合计6。0尾及4–9尾各最多3个，1–3尾各最多4个", Shape.ENDINGS),
    BLUE_TEMPERATURE("blueTemperature", "冷温热", true, "coldHot", "只填冷、温或热，沿用输入图表的分类", Shape.BAND),
    BLUE_PARITY("blueParity", "奇偶", true, "oddEven", "只填奇或偶", Shape.PARITY),
    BLUE_ROAD("blueRoad", "012路", true, "road", "只填0路、1路或2路", Shape.ROAD),
    BLUE_SIZE("blueSize", "大小", true, "size", "只填小或大，小为1–8，大为9–16", Shape.SIZE),
    BLUE_INTERVAL("blueInterval", "四区间分布", true, "intervals", "只填1-4、5-8、9-12或13-16", Shape.INTERVAL);

    private static final Pattern ROAD_VALUE = Pattern.compile("([0-2０-２])(?:\\s*路)?", Pattern.UNICODE_CHARACTER_CLASS);

    private final String code;
    private final String label;
    private final boolean blue;
    private final List<String> groups;
    private final String valueHint;
    private final Shape shape;

    FeatureTrendKind(String code, String label, boolean blue, String groups, String valueHint, Shape shape) {
        this.code = code;
        this.label = label;
        this.blue = blue;
        this.groups = List.of(groups.split(" "));
        this.valueHint = valueHint;
        this.shape = shape;
    }

    public String code() { return code; }
    public String label() { return label; }
    public boolean blue() { return blue; }
    public List<String> groups() { return groups; }
    public String valueHint() { return valueHint; }

    public static FeatureTrendKind fromCode(String code) {
        return Arrays.stream(values()).filter(kind -> kind.code.equals(code)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("无效的推演特征"));
    }

    public String normalizeValue(String value) {
        if (StringUtils.isBlank(value) || value.length() > 64) {
            throw new IllegalArgumentException("候选值为空或过长");
        }
        String normalized = value.strip().replace('：', ':').replace('｜', '|')
            .replace('–', '-').replace('—', '-').replace(" ", "");
        boolean valid = switch (shape) {
            case PAIR -> total(counts(normalized, 2)) == 6;
            case TRIPLE -> total(counts(normalized, 3)) == 6;
            case SUM -> inRange(normalized, 21, 183);
            case SPAN -> inRange(normalized, 5, 32);
            case ROAD -> ROAD_VALUE.matcher(normalized).matches();
            case BAND -> Set.of("冷", "温", "热").contains(normalized);
            case PARITY -> Set.of("奇", "偶").contains(normalized);
            case SIZE -> Set.of("小", "大").contains(normalized);
            case INTERVAL -> Set.of("1-4", "5-8", "9-12", "13-16").contains(normalized);
            case ENDINGS -> validEndings(counts(normalized, 10));
            case TEMPERATURE -> validTemperature(normalized);
        };
        if (!valid) {
            String constraint = shape == Shape.ROAD ? "只接受单个路数（0路、1路或2路）" : "候选不符合取值范围或合计约束";
            throw new IllegalArgumentException(label + constraint);
        }
        return shape == Shape.ROAD ? Character.digit(normalized.charAt(0), 10) + "路" : normalized;
    }

    private static boolean validEndings(int[] values) {
        if (total(values) != 6) return false;
        for (int digit = 0; digit < values.length; digit++) {
            if (values[digit] > (digit >= 1 && digit <= 3 ? 4 : 3)) return false;
        }
        return true;
    }

    private static boolean validTemperature(String value) {
        var parts = value.split("\\|", -1);
        if (parts.length != 4) return false;
        int[][] rows = Arrays.stream(parts).map(part -> counts(part, 3)).toArray(int[][]::new);
        if (Arrays.stream(rows).anyMatch(row -> row.length != 3) || total(rows[0]) != 6) return false;
        for (int band = 0; band < 3; band++) {
            if (rows[0][band] != rows[1][band] + rows[2][band] + rows[3][band]) return false;
        }
        return true;
    }

    private static int[] counts(String value, int length) {
        if (!value.matches("[0-6](?::[0-6]){" + (length - 1) + "}")) return new int[0];
        return value.chars().filter(c -> c != ':').map(c -> c - '0').toArray();
    }

    private static int total(int[] values) { return Arrays.stream(values).sum(); }

    private static boolean inRange(String value, int min, int max) {
        if (!value.matches("[1-9][0-9]{0,2}")) return false;
        int number = Integer.parseInt(value);
        return number >= min && number <= max;
    }

    private enum Shape { PAIR, TRIPLE, TEMPERATURE, SUM, SPAN, ROAD, BAND, PARITY, SIZE, INTERVAL, ENDINGS }
}
