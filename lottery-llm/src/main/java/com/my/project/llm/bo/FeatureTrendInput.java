package com.my.project.llm.bo;

import java.util.List;

/** The same chronological snapshot is used to render charts and check model evidence. */
public record FeatureTrendInput(String endPeriod, List<String> periods, List<Group> groups) {
    public record Group(String code, String label, String description, boolean occurrence,
                        int min, int max, List<Series> series) { }

    public record Series(String code, String label, List<Integer> values) { }
}
