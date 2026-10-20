package com.my.project.llm.bo;

import java.util.List;

public record FeatureTrendInference(String observation, List<Candidate> candidates, String counterEvidence) {
    public record Candidate(String value, String reason, String caution, List<String> evidencePeriods) { }
}
