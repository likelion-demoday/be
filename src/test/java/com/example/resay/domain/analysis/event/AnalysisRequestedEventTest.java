package com.example.resay.domain.analysis.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisRequestedEventTest {

    @Test
    void createsEvent() {
        AnalysisRequestedEvent event = new AnalysisRequestedEvent(1L);

        assertThat(event.recordingId()).isEqualTo(1L);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidRecordingId(Long recordingId) {
        assertThatThrownBy(() -> new AnalysisRequestedEvent(recordingId))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
