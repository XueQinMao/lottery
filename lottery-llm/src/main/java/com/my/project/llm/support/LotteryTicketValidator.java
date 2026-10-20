package com.my.project.llm.support;

import java.util.List;

/** 校验单式、复式号码并计算组合数。 */
public final class LotteryTicketValidator {
    private LotteryTicketValidator() { }

    public static void checkSingle(List<Integer> reds, Integer blue) {
        numbers(reds, 6, 6, 33);
        require(blue != null && blue >= 1 && blue <= 16, "蓝球范围错误");
    }

    public static void numbers(List<Integer> values, int min, int max, int upper) {
        require(values != null && values.size() >= min && values.size() <= max, "号码个数错误");
        int previous = 0;
        for (Integer value : values) {
            require(value != null && value > previous && value <= upper, "号码必须在合法范围内互异升序");
            previous = value;
        }
    }

    public static int combinations(int n, int k) {
        int result = 1;
        for (int index = 1; index <= k; index++) result = result * (n - index + 1) / index;
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
