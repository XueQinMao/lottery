package com.my.project.application.support;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.regex.Pattern;

final class ForecastBucketMatcher {
    private static final Pattern NUMERIC_RANGE = Pattern.compile("\\d+-\\d+");

    private ForecastBucketMatcher() {
    }

    static <T> T match(String value, List<T> buckets, boolean allowRange,
                       Function<T, String> ratio, ToDoubleFunction<T> score) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String normalized = value.trim();
        for (T bucket : buckets) {
            if (normalized.equals(ratio.apply(bucket))) {
                return bucket;
            }
        }

        NumericRange range = allowRange ? parseRange(normalized) : null;
        if (range == null) {
            return null;
        }
        T best = null;
        for (T bucket : buckets) {
            Integer number = parseInteger(ratio.apply(bucket));
            if (number == null || !range.contains(number)) {
                continue;
            }
            if (best == null || score.applyAsDouble(bucket) > score.applyAsDouble(best)) {
                best = bucket;
            }
        }
        return best;
    }

    static NumericRange parseRange(String value) {
        if (value == null || !NUMERIC_RANGE.matcher(value).matches()) {
            return null;
        }
        int dash = value.indexOf('-');
        try {
            return new NumericRange(Integer.parseInt(value, 0, dash, 10),
                Integer.parseInt(value, dash + 1, value.length(), 10));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInteger(String value) {
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    record NumericRange(int start, int end) {
        boolean contains(int value) {
            return value >= start && value <= end;
        }
    }
}
