package com.my.project.application.learning;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LearningPolicyTest {
    @Test
    void waitsForThirtyPairedDrawsBeforePromotingAConsistentImprovement() {
        var experiment = LearningPolicy.Track.initial().start("SWAP_TOP_TWO");
        for (int index = 0; index < 29; index++) {
            experiment = LearningPolicy.record("redZones", experiment, index < 12 ? 1 : 0, "2026133");
        }
        assertThat(experiment.active().number()).isEqualTo(1);
        assertThat(experiment.progress().periods()).isEqualTo(29);

        var accepted = LearningPolicy.record("redZones", experiment, 0, "2026134");
        assertThat(accepted.active().number()).isEqualTo(2);
        assertThat(accepted.trial()).isNull();
        assertThat(accepted.lastDecision().promoted()).isTrue();
    }

    @Test
    void retainsTheActiveVersionAndKeepsRejectedVersionsUnique() {
        var experiment = LearningPolicy.Track.initial().start("redZones");
        for (int index = 0; index < 30; index++) {
            experiment = LearningPolicy.record(LearningPolicy.NUMBERS, experiment, index % 2 == 0 ? 1 : -1, "2026134");
        }
        assertThat(experiment.active().number()).isEqualTo(1);
        assertThat(experiment.lastDecision().promoted()).isFalse();
        assertThat(experiment.start("blueSize").trial().number()).isEqualTo(3);
    }
}
