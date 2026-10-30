package com.example.resay.domain.analysis.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisSegmentTest {

    @Test
    void createsAnalysisSegment() {
        AnalysisSegment segment = new AnalysisSegment(1L, SpeakerRole.SELF, 100L, 500L, "안녕");

        assertThat(segment.segmentId()).isEqualTo(1L);
        assertThat(segment.speakerRole()).isEqualTo(SpeakerRole.SELF);
        assertThat(segment.startMs()).isEqualTo(100L);
        assertThat(segment.endMs()).isEqualTo(500L);
        assertThat(segment.content()).isEqualTo("안녕");
    }

    @Test
    void rejectsInvalidTimeRange() {
        assertThatThrownBy(() -> new AnalysisSegment(1L, SpeakerRole.SELF, 500L, 500L, "안녕"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlankContent() {
        assertThatThrownBy(() -> new AnalysisSegment(1L, SpeakerRole.SELF, 100L, 500L, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
