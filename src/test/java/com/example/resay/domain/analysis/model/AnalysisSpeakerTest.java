package com.example.resay.domain.analysis.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisSpeakerTest {

    @Test
    void createsSpeakerWithTrimmedName() {
        AnalysisSpeaker speaker = new AnalysisSpeaker(SpeakerRole.SELF, "  호준  ");

        assertThat(speaker.speakerRole()).isEqualTo(SpeakerRole.SELF);
        assertThat(speaker.speakerName()).isEqualTo("호준");
    }

    @Test
    void rejectsMissingRole() {
        assertThatThrownBy(() -> new AnalysisSpeaker(null, "호준"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("speakerRole");
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> new AnalysisSpeaker(SpeakerRole.SELF, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("speakerName");
    }
}
