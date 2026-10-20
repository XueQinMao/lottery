package com.my.project.llm.bo;

import java.util.List;

/** Both image rendering and model facts use this chronological, fixed-cutoff snapshot. */
public record ZoneTrendSnapshot(String endPeriod, List<String> periods, List<Series> counts, List<Series> ratios) {
    public record Series(String code, List<Integer> values) { }
}
