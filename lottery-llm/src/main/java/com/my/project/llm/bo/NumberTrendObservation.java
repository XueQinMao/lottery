package com.my.project.llm.bo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NumberTrendObservation(Ball ball, int number, List<Evidence> evidence, String observation) {
    public NumberTrendObservation {
        if (evidence != null) evidence = List.copyOf(evidence);
    }

    public NumberTrendObservation(Ball ball, int number) {
        this(ball, number, null, null);
    }

    public enum Ball {
        RED(33), BLUE(16);

        private final int count;

        Ball(int count) {
            this.count = count;
        }

        public int count() {
            return count;
        }
    }

    public record Evidence(String period, int omission, Integer number) {
        public Evidence(String period, int omission) {
            this(period, omission, null);
        }
    }
}
