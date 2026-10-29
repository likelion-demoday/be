package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import com.example.resay.domain.analysis.entity.AnalysisStatus;
import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import com.example.resay.domain.analysis.event.AnalysisFailedEvent;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.repository.RecordingRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AnalysisStallServiceTest {

    @Mock
    private ConversationAnalysisRepository conversationAnalysisRepository;

    @Mock
    private RecordingRepository recordingRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Recording recording;

    @Test
    void publishesFailureOnlyWhenConditionalUpdateSucceeds() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 10, 29, 10, 0);
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 11, 0);
        given(conversationAnalysisRepository.markStalledFailed(10L, 20L, cutoff, now))
                .willReturn(1);

        assertThat(service().failStalledAnalysis(10L, 20L, cutoff, now)).isTrue();

        then(eventPublisher).should().publishEvent(
                new AnalysisFailedEvent(20L, AnalysisFailureReason.PROCESSING_ERROR)
        );
    }

    @Test
    void doesNotPublishFailureWhenAnalysisAlreadyChanged() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 10, 29, 10, 0);
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 11, 0);
        given(conversationAnalysisRepository.markStalledFailed(10L, 20L, cutoff, now))
                .willReturn(0);

        assertThat(service().failStalledAnalysis(10L, 20L, cutoff, now)).isFalse();

        then(eventPublisher).shouldHaveNoInteractions();
    }

    @Test
    void createsFailedAnalysisForStalledRecordingWithoutAnalysis() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 10, 29, 10, 0);
        given(recordingRepository.findByIdForUpdate(20L)).willReturn(Optional.of(recording));
        given(recording.getStatus()).willReturn(RecordingStatus.ANALYZING);
        given(recording.getUpdatedAt()).willReturn(cutoff.minusMinutes(1));
        given(conversationAnalysisRepository.existsByRecordingId(20L)).willReturn(false);

        assertThat(service().failStalledRecordingWithoutAnalysis(20L, cutoff)).isTrue();

        ArgumentCaptor<ConversationAnalysis> captor =
                ArgumentCaptor.forClass(ConversationAnalysis.class);
        then(conversationAnalysisRepository).should().saveAndFlush(captor.capture());
        assertThat(captor.getValue().getRecordingId()).isEqualTo(20L);
        assertThat(captor.getValue().getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(captor.getValue().getFailureReason())
                .isEqualTo(AnalysisFailureReason.PROCESSING_ERROR);
        then(eventPublisher).should().publishEvent(
                new AnalysisFailedEvent(20L, AnalysisFailureReason.PROCESSING_ERROR)
        );
    }

    @Test
    void leavesRecentRecordingWithoutAnalysisAlone() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 10, 29, 10, 0);
        given(recordingRepository.findByIdForUpdate(20L)).willReturn(Optional.of(recording));
        given(recording.getStatus()).willReturn(RecordingStatus.ANALYZING);
        given(recording.getUpdatedAt()).willReturn(cutoff.plusMinutes(1));

        assertThat(service().failStalledRecordingWithoutAnalysis(20L, cutoff)).isFalse();

        then(conversationAnalysisRepository).shouldHaveNoInteractions();
        then(eventPublisher).shouldHaveNoInteractions();
    }

    private AnalysisStallService service() {
        return new AnalysisStallService(
                conversationAnalysisRepository,
                recordingRepository,
                eventPublisher
        );
    }
}
