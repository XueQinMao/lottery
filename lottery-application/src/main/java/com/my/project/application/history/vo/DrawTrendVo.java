package com.my.project.application.history.vo;

import java.util.List;

public record DrawTrendVo(List<String> periods, List<Draw> draws, List<Group> redFeatures,
                          List<Group> blueFeatures, String coldHotBasis) {
    public DrawTrendVo {
        periods = List.copyOf(periods);
        draws = List.copyOf(draws);
        redFeatures = List.copyOf(redFeatures);
        blueFeatures = List.copyOf(blueFeatures);
    }

    public record Draw(String period, List<Integer> reds, Integer blue) {
        public Draw { reds = List.copyOf(reds); }
    }

    public record Group(String code, String label, String description, boolean occurrence,
                        int min, int max, List<Series> series) {
        public Group { series = List.copyOf(series); }
    }

    public record Series(String code, String label, List<Integer> values, double average,
                         int total, int sampleCount) {
        public Series {
            // null denotes a draw without the 30 preceding records required for classification.
            values = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(values));
        }
    }
}
