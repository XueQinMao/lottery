package com.my.project.llm.bo;

import java.util.List;

public record ZoneTrendAnalysisBo(String model, String generatedAt, String fileName,
                                  ZoneTrendSnapshot input, List<ImageInfo> images, ZoneTrendInference inference) {
    public record ImageInfo(String code, String sha256, int width, int height) { }
}
