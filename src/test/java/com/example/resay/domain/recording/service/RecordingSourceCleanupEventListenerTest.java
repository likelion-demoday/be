package com.example.resay.domain.recording.service;

import com.example.resay.domain.analysis.event.AnalysisCompletedEvent;
import com.example.resay.domain.recording.entity.RecordingFailureReason;
import com.example.resay.domain.recording.event.RecordingFailedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordingSourceCleanupEventListenerTest {

    @Mock
    private RecordingSourceCleanupService cleanupService;

    @InjectMocks
    private RecordingSourceCleanupEventListener listener;

    @Test
    void 분석_완료_후_원본을_정리한다() {
        when(cleanupService.deleteSourceData(1L)).thenReturn(true);

        listener.onAnalysisCompleted(new AnalysisCompletedEvent(10L, 1L));

        verify(cleanupService).deleteSourceData(1L);
    }

    @Test
    void 녹음_실패_후_원본을_정리한다() {
        when(cleanupService.deleteSourceData(1L)).thenReturn(true);

        listener.onRecordingFailed(new RecordingFailedEvent(
                1L,
                RecordingFailureReason.TRANSCRIPTION_TIMEOUT
        ));

        verify(cleanupService).deleteSourceData(1L);
    }

    @Test
    void 정리_예외가_다른_완료_리스너로_전파되지_않는다() {
        when(cleanupService.deleteSourceData(1L)).thenThrow(new IllegalStateException("DB 오류"));

        assertThatCode(() -> listener.onAnalysisCompleted(new AnalysisCompletedEvent(10L, 1L)))
                .doesNotThrowAnyException();
    }
}
