package com.my.project.api.llm;

import com.my.project.api.request.LlmAdjustmentRequest;
import com.my.project.api.request.LlmRecommendationTaskRequest;
import com.my.project.application.feature.dto.LlmAdjustmentCommand;
import com.my.project.application.feature.dto.LlmRecommendationTaskCommand;
import com.my.project.application.feature.dto.RecommendationCommand;
import com.my.project.application.feature.enums.LlmAdjustTaskMode;
import org.apache.commons.collections4.CollectionUtils;

/** HTTP DTOs stay at the edge; application commands carry no servlet dependencies. */
public final class LlmRequestMapper {
    private LlmRequestMapper() { }

    public static RecommendationCommand recommendation(LlmAdjustmentRequest input) {
        var request = input == null ? new LlmAdjustmentRequest() : input;
        return switch (mode(request.getMode())) {
            case FEATURE -> new RecommendationCommand.Feature(LlmAdjustmentCommand.builder()
                .drawRecords(CollectionUtils.emptyIfNull(request.getDrawRecords()).stream()
                    .map(draw -> LlmAdjustmentCommand.DrawRecord.builder()
                        .redBalls(draw.getRedBalls()).blueBall(draw.getBlueBall()).build()).toList())
                .lastDrawRedBalls(request.getLastDrawRedBalls())
                .lastDrawBlueBall(request.getLastDrawBlueBall()).count(request.getCount())
                .userRequirement(request.getUserRequirement()).build());
            case CACHE -> new RecommendationCommand.Cache(request.getCount() == null ? 2 : request.getCount(),
                request.getIsTopN() == null || request.getIsTopN(), request.getUserRequirement());
        };
    }

    public static LlmRecommendationTaskCommand task(LlmRecommendationTaskRequest input) {
        var request = input == null ? new LlmRecommendationTaskRequest() : input;
        return LlmRecommendationTaskCommand.builder().mode(mode(request.getMode()))
            .count(request.getCount()).isTopN(request.getIsTopN())
            .userRequirement(request.getUserRequirement()).build();
    }

    private static LlmAdjustTaskMode mode(String value) {
        if (value == null || value.isBlank()) return LlmAdjustTaskMode.FEATURE;
        var mode = LlmAdjustTaskMode.of(value);
        if (mode == null) throw new IllegalArgumentException("mode 必须为 FEATURE 或 CACHE");
        return mode;
    }
}
