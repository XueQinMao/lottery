package com.my.project.application.history.support;

import java.util.List;
import java.util.Objects;

final class TrendStatistics {
    private TrendStatistics() { }

    static double average(List<Integer> values) {
        double mean = values.stream().filter(Objects::nonNull).mapToInt(Integer::intValue).average().orElse(0);
        return Math.round(mean * 100.0) / 100.0;
    }
}
