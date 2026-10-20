package com.my.project.application.history.vo;

import java.util.List;

public record NumberTrendVo(List<Row> rows, List<NumberStats> redStats, List<NumberStats> blueStats,
                            List<Integer> redCandidates) {
    public NumberTrendVo {
        rows = List.copyOf(rows);
        redStats = List.copyOf(redStats);
        blueStats = List.copyOf(blueStats);
        redCandidates = List.copyOf(redCandidates);
    }

    public record Row(String period, String openDate, List<Integer> reds, int blue,
                      List<Integer> redOmissions, List<Integer> blueOmissions) {
        public Row {
            reds = List.copyOf(reds);
            redOmissions = List.copyOf(redOmissions);
            blueOmissions = List.copyOf(blueOmissions);
        }
    }

    public record NumberStats(int number, int hitCount, Double averageOmission, int maxOmission,
                              int maxConsecutive, int currentOmission) { }
}
