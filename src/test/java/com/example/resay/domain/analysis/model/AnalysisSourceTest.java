package com.example.resay.domain.analysis.model;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisSourceTest {

    @Test
    void createsAnalysisSource() {
        AnalysisSource source = new AnalysisSource(
                1L,
                AnalysisScenario.COUPLE_DAILY,
                10_000L,
                coupleSegments()
        );

        assertThat(source.recordingId()).isEqualTo(1L);
        assertThat(source.scenario()).isEqualTo(AnalysisScenario.COUPLE_DAILY);
        assertThat(source.durationMs()).isEqualTo(10_000L);
        assertThat(source.segments()).hasSize(2);
    }

    @Test
    void copiesSegmentsDefensively() {
        List<AnalysisSegment> segments = new ArrayList<>(coupleSegments());
        AnalysisSource source = new AnalysisSource(
                1L,
                AnalysisScenario.COUPLE_DAILY,
                10_000L,
                segments
        );

        segments.clear();

        assertThat(source.segments()).hasSize(2);
        assertThatThrownBy(() -> source.segments().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicateSegmentId() {
        List<AnalysisSegment> segments = List.of(
                new AnalysisSegment(1L, SpeakerRole.SELF, 100L, 500L, "안녕"),
                new AnalysisSegment(1L, SpeakerRole.PARTNER, 600L, 900L, "반가워")
        );

        assertThatThrownBy(() -> new AnalysisSource(
                1L,
                AnalysisScenario.COUPLE_DAILY,
                10_000L,
                segments
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnorderedSegments() {
        List<AnalysisSegment> segments = List.of(
                new AnalysisSegment(1L, SpeakerRole.SELF, 600L, 900L, "안녕"),
                new AnalysisSegment(2L, SpeakerRole.PARTNER, 100L, 500L, "반가워")
        );

        assertThatThrownBy(() -> new AnalysisSource(
                1L,
                AnalysisScenario.COUPLE_DAILY,
                10_000L,
                segments
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingSpeakerRole() {
        List<AnalysisSegment> segments = List.of(
                new AnalysisSegment(1L, SpeakerRole.SELF, 100L, 500L, "안녕"),
                new AnalysisSegment(2L, SpeakerRole.SELF, 600L, 900L, "잘 지내?")
        );

        assertThatThrownBy(() -> new AnalysisSource(
                1L,
                AnalysisScenario.COUPLE_DAILY,
                10_000L,
                segments
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsupportedSpeakerRole() {
        List<AnalysisSegment> segments = List.of(
                new AnalysisSegment(1L, SpeakerRole.SELF, 100L, 500L, "안녕"),
                new AnalysisSegment(2L, SpeakerRole.FRIEND, 600L, 900L, "반가워")
        );

        assertThatThrownBy(() -> new AnalysisSource(
                1L,
                AnalysisScenario.COUPLE_DAILY,
                10_000L,
                segments
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private List<AnalysisSegment> coupleSegments() {
        return List.of(
                new AnalysisSegment(1L, SpeakerRole.SELF, 100L, 500L, "안녕"),
                new AnalysisSegment(2L, SpeakerRole.PARTNER, 600L, 900L, "반가워")
        );
    }
}
