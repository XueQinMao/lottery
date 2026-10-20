package com.my.project.application.feature.dto;

import com.my.project.llm.bo.ColdHotAnalysisBo;
import com.my.project.llm.bo.FeatureTrendInference;
import com.my.project.llm.bo.FeatureTrendInput;
import com.my.project.llm.bo.ZoneTrendAnalysisBo.ImageInfo;
import com.my.project.llm.bo.ZoneTrendImage;

import java.time.Instant;
import java.util.List;

public final class FeatureTrendForecast {
    private FeatureTrendForecast() { }

    public record Plan(String sourcePeriod, String targetPeriod, List<Item> items, ColdHotAnalysisBo coldHotAnalysis) { }

    public record Item(String code, String label, boolean blue, FeatureTrendInput input) { }

    public record Run(String featureCode, FeatureTrendInput input, List<ZoneTrendImage> images) { }

    public record Summary(String sourcePeriod, String targetPeriod, String featureCode, String label,
                          boolean blue, String model, List<String> values, Instant generatedAt) { }

    public record Detail(Summary summary, Evidence evidence) { }

    public record Evidence(FeatureTrendInput input, List<ImageInfo> images, FeatureTrendInference inference) { }
}
