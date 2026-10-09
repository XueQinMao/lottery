package com.my.project.application.feature.dto;

/** The two supported recommendation sources, shared by synchronous and task APIs. */
public sealed interface RecommendationCommand {

    record Feature(LlmAdjustmentCommand adjustment) implements RecommendationCommand { }

    record Cache(Integer count, boolean topN, String userRequirement) implements RecommendationCommand { }
}
