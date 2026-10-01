package com.example.resay.domain.analysis.model;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisScenarioTest {

    @Test
    void containsOnlySupportedScenarios() {
        assertThat(AnalysisScenario.values()).containsExactly(
                AnalysisScenario.FRIEND_DAILY,
                AnalysisScenario.COUPLE_DAILY,
                AnalysisScenario.COUPLE_CONFLICT,
                AnalysisScenario.PARENT_CHILD_CONFLICT
        );
    }

    @Test
    void definesRequiredSpeakerRoles() {
        assertThat(AnalysisScenario.FRIEND_DAILY.requiredRoles())
                .isEqualTo(Set.of(SpeakerRole.SELF, SpeakerRole.FRIEND));
        assertThat(AnalysisScenario.COUPLE_DAILY.requiredRoles())
                .isEqualTo(Set.of(SpeakerRole.SELF, SpeakerRole.PARTNER));
        assertThat(AnalysisScenario.COUPLE_CONFLICT.requiredRoles())
                .isEqualTo(Set.of(SpeakerRole.SELF, SpeakerRole.PARTNER));
        assertThat(AnalysisScenario.PARENT_CHILD_CONFLICT.requiredRoles())
                .isEqualTo(Set.of(SpeakerRole.PARENT, SpeakerRole.CHILD));
    }
}
