package com.example.resay.domain.analysis.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationAnalysisTest {

    @Test
    void startsAnalysisWithAnalyzingStatus() {
        ConversationAnalysis analysis = ConversationAnalysis.start(1L);

        assertThat(analysis.getRecordingId()).isEqualTo(1L);
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.ANALYZING);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidRecordingId(Long recordingId) {
        assertThatThrownBy(() -> ConversationAnalysis.start(recordingId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void completesAnalyzingAnalysis() {
        ConversationAnalysis analysis = ConversationAnalysis.start(1L);

        analysis.complete();

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
    }

    @Test
    void failsAnalyzingAnalysis() {
        ConversationAnalysis analysis = ConversationAnalysis.start(1L);

        analysis.fail();

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
    }

    @Test
    void rejectsCompletingCompletedAnalysis() {
        ConversationAnalysis analysis = ConversationAnalysis.start(1L);
        analysis.complete();

        assertThatThrownBy(analysis::complete)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsFailingCompletedAnalysis() {
        ConversationAnalysis analysis = ConversationAnalysis.start(1L);
        analysis.complete();

        assertThatThrownBy(analysis::fail)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsCompletingFailedAnalysis() {
        ConversationAnalysis analysis = ConversationAnalysis.start(1L);
        analysis.fail();

        assertThatThrownBy(analysis::complete)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsFailingFailedAnalysis() {
        ConversationAnalysis analysis = ConversationAnalysis.start(1L);
        analysis.fail();

        assertThatThrownBy(analysis::fail)
                .isInstanceOf(IllegalStateException.class);
    }
}
