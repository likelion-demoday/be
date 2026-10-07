package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.code.AnalysisErrorCode;
import com.example.resay.domain.analysis.dto.AnalysisResultCommand;
import com.example.resay.domain.analysis.entity.AnalysisResult;
import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import com.example.resay.domain.analysis.entity.AnalysisStatus;
import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import com.example.resay.domain.analysis.event.AnalysisCompletedEvent;
import com.example.resay.domain.analysis.event.AnalysisFailedEvent;
import com.example.resay.domain.analysis.repository.AnalysisResultRepository;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.global.exception.GeneralException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    @Mock
    private ConversationAnalysisRepository conversationAnalysisRepository;

    @Mock
    private AnalysisResultRepository analysisResultRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AnalysisService analysisService;

    @Test
    void startsAnalysis() {
        given(conversationAnalysisRepository.existsByRecordingId(1L)).willReturn(false);

        analysisService.start(1L);

        ArgumentCaptor<ConversationAnalysis> captor = ArgumentCaptor.forClass(ConversationAnalysis.class);
        then(conversationAnalysisRepository).should().saveAndFlush(captor.capture());
        assertThat(captor.getValue().getRecordingId()).isEqualTo(1L);
        assertThat(captor.getValue().getStatus()).isEqualTo(AnalysisStatus.ANALYZING);
    }

    @Test
    void rejectsExistingAnalysis() {
        given(conversationAnalysisRepository.existsByRecordingId(1L)).willReturn(true);

        assertThatThrownBy(() -> analysisService.start(1L))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AnalysisErrorCode.ANALYSIS_ALREADY_EXISTS)
                );

        then(conversationAnalysisRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    void handlesConcurrentDuplicateAnalysis() {
        given(conversationAnalysisRepository.existsByRecordingId(1L)).willReturn(false);
        given(conversationAnalysisRepository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
                .willThrow(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> analysisService.start(1L))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AnalysisErrorCode.ANALYSIS_ALREADY_EXISTS)
                );
    }

    @Test
    void completesAnalysis() {
        ConversationAnalysis analysis = savedAnalysis();
        given(conversationAnalysisRepository.findByRecordingId(1L)).willReturn(Optional.of(analysis));
        given(analysisResultRepository.existsByAnalysisId(10L)).willReturn(false);

        analysisService.complete(1L, resultCommand());

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        ArgumentCaptor<AnalysisResult> captor = ArgumentCaptor.forClass(AnalysisResult.class);
        then(analysisResultRepository).should().saveAndFlush(captor.capture());
        assertThat(captor.getValue().getAnalysisId()).isEqualTo(10L);
        assertThat(captor.getValue().getResultJson()).isEqualTo("{\"summary\":\"대화 요약\"}");
        then(eventPublisher).should().publishEvent(new AnalysisCompletedEvent(10L, 1L));
    }

    @Test
    void failsAnalysis() {
        ConversationAnalysis analysis = ConversationAnalysis.start(1L);
        given(conversationAnalysisRepository.findByRecordingId(1L)).willReturn(Optional.of(analysis));

        analysisService.fail(1L, AnalysisFailureReason.PROCESSING_ERROR);

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.getFailureReason()).isEqualTo(AnalysisFailureReason.PROCESSING_ERROR);
        then(eventPublisher).should().publishEvent(
                new AnalysisFailedEvent(1L, AnalysisFailureReason.PROCESSING_ERROR)
        );
    }

    @Test
    void recordsFailureForAnalyzingAnalysis() {
        ConversationAnalysis analysis = ConversationAnalysis.start(1L);
        given(conversationAnalysisRepository.findByRecordingIdForUpdate(1L))
                .willReturn(Optional.of(analysis));

        boolean recorded = analysisService.recordFailure(
                1L,
                AnalysisFailureReason.PROCESSING_ERROR
        );

        assertThat(recorded).isTrue();
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        then(eventPublisher).should().publishEvent(
                new AnalysisFailedEvent(1L, AnalysisFailureReason.PROCESSING_ERROR)
        );
    }

    @Test
    void createsFailedAnalysisWhenFailureOccursBeforeStart() {
        given(conversationAnalysisRepository.findByRecordingIdForUpdate(1L))
                .willReturn(Optional.empty());

        boolean recorded = analysisService.recordFailure(
                1L,
                AnalysisFailureReason.PROCESSING_ERROR
        );

        assertThat(recorded).isTrue();
        ArgumentCaptor<ConversationAnalysis> captor =
                ArgumentCaptor.forClass(ConversationAnalysis.class);
        then(conversationAnalysisRepository).should().saveAndFlush(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(captor.getValue().getFailureReason())
                .isEqualTo(AnalysisFailureReason.PROCESSING_ERROR);
        then(eventPublisher).should().publishEvent(
                new AnalysisFailedEvent(1L, AnalysisFailureReason.PROCESSING_ERROR)
        );
    }

    @Test
    void doesNotPublishDuplicateFailureForTerminalAnalysis() {
        ConversationAnalysis analysis = ConversationAnalysis.start(1L);
        analysis.fail(AnalysisFailureReason.PROCESSING_ERROR);
        given(conversationAnalysisRepository.findByRecordingIdForUpdate(1L))
                .willReturn(Optional.of(analysis));

        boolean recorded = analysisService.recordFailure(
                1L,
                AnalysisFailureReason.PROCESSING_ERROR
        );

        assertThat(recorded).isFalse();
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    void rejectsMissingAnalysis() {
        given(conversationAnalysisRepository.findByRecordingId(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> analysisService.complete(1L, resultCommand()))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AnalysisErrorCode.ANALYSIS_NOT_FOUND)
                );
    }

    @Test
    void rejectsExistingAnalysisResult() {
        ConversationAnalysis analysis = savedAnalysis();
        given(conversationAnalysisRepository.findByRecordingId(1L)).willReturn(Optional.of(analysis));
        given(analysisResultRepository.existsByAnalysisId(10L)).willReturn(true);

        assertThatThrownBy(() -> analysisService.complete(1L, resultCommand()))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AnalysisErrorCode.ANALYSIS_RESULT_ALREADY_EXISTS)
                );

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.ANALYZING);
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    void handlesConcurrentDuplicateAnalysisResult() {
        ConversationAnalysis analysis = savedAnalysis();
        given(conversationAnalysisRepository.findByRecordingId(1L)).willReturn(Optional.of(analysis));
        given(analysisResultRepository.existsByAnalysisId(10L)).willReturn(false);
        given(analysisResultRepository.saveAndFlush(org.mockito.ArgumentMatchers.any()))
                .willThrow(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> analysisService.complete(1L, resultCommand()))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AnalysisErrorCode.ANALYSIS_RESULT_ALREADY_EXISTS)
                );
        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    void rejectsInvalidStatusChange() {
        ConversationAnalysis analysis = ConversationAnalysis.start(1L);
        analysis.complete();
        given(conversationAnalysisRepository.findByRecordingId(1L)).willReturn(Optional.of(analysis));

        assertThatThrownBy(() ->
                analysisService.fail(1L, AnalysisFailureReason.PROCESSING_ERROR)
        )
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AnalysisErrorCode.INVALID_ANALYSIS_STATUS)
                );
    }

    private ConversationAnalysis savedAnalysis() {
        ConversationAnalysis analysis = ConversationAnalysis.start(1L);
        ReflectionTestUtils.setField(analysis, "id", 10L);
        return analysis;
    }

    private AnalysisResultCommand resultCommand() {
        return new AnalysisResultCommand(
                "{\"summary\":\"대화 요약\"}",
                "liner-mark-1.1",
                "v1",
                "v1"
        );
    }
}
