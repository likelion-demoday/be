package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.domain.recording.service.AnalysisFailureRecordingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AnalysisFailureReconcilerTest {

    @Mock
    private ConversationAnalysisRepository conversationAnalysisRepository;

    @Mock
    private AnalysisFailureRecordingService analysisFailureRecordingService;

    @Test
    void reconnectsFailedAnalysisToRecordingFailureFlow() {
        ConversationAnalysis analysis = failedAnalysis(
                10L,
                AnalysisFailureReason.EXTERNAL_SERVICE_UNAVAILABLE
        );
        given(conversationAnalysisRepository.findFailedWithAnalyzingRecording())
                .willReturn(List.of(analysis));
        given(analysisFailureRecordingService.recordFailure(
                10L,
                AnalysisFailureReason.EXTERNAL_SERVICE_UNAVAILABLE
        )).willReturn(true);

        assertThat(reconciler().reconcileFailedAnalyses()).isEqualTo(1);

        then(analysisFailureRecordingService).should().recordFailure(
                10L,
                AnalysisFailureReason.EXTERNAL_SERVICE_UNAVAILABLE
        );
    }

    @Test
    void continuesWhenOneRecordingFailsToRecover() {
        ConversationAnalysis first = failedAnalysis(10L, AnalysisFailureReason.PROCESSING_ERROR);
        ConversationAnalysis second = failedAnalysis(20L, AnalysisFailureReason.INVALID_ANALYSIS_RESPONSE);
        given(conversationAnalysisRepository.findFailedWithAnalyzingRecording())
                .willReturn(List.of(first, second));
        given(analysisFailureRecordingService.recordFailure(10L, AnalysisFailureReason.PROCESSING_ERROR))
                .willThrow(new IllegalStateException("database unavailable"));
        given(analysisFailureRecordingService.recordFailure(
                20L,
                AnalysisFailureReason.INVALID_ANALYSIS_RESPONSE
        )).willReturn(true);

        assertThat(reconciler().reconcileFailedAnalyses()).isEqualTo(1);

        then(analysisFailureRecordingService).should().recordFailure(
                20L,
                AnalysisFailureReason.INVALID_ANALYSIS_RESPONSE
        );
    }

    private AnalysisFailureReconciler reconciler() {
        return new AnalysisFailureReconciler(
                conversationAnalysisRepository,
                analysisFailureRecordingService
        );
    }

    private ConversationAnalysis failedAnalysis(
            Long recordingId,
            AnalysisFailureReason failureReason
    ) {
        ConversationAnalysis analysis = ConversationAnalysis.start(recordingId);
        analysis.fail(failureReason);
        return analysis;
    }
}
