package com.my.project.llm.bo;

import java.util.List;

public record ZoneTrendInference(List<CountTrend> countTrends, String ratioTrend,
                                 List<Candidate> candidates, String counterEvidence) {
    public record CountTrend(int zone, String observation, String nextInference, List<String> evidencePeriods) { }
    public record Candidate(String ratio, String reason, String caution, List<String> evidencePeriods) { }
}
