package com.example.resay.domain.transcription.scheduler;

import com.example.resay.domain.transcription.service.TranscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranscriptionTimeoutSchedulerTest {

    @Mock
    private TranscriptionService transcriptionService;

    @InjectMocks
    private TranscriptionTimeoutScheduler scheduler;

    @Test
    void 결제_후_시작되지_않은_전사를_대신_시작한다() {
        when(transcriptionService.findTranscriptionNotStartedIds(any())).thenReturn(List.of(1L, 2L));

        scheduler.startUnstartedTranscriptions();

        verify(transcriptionService).start(1L);
        verify(transcriptionService).start(2L);
    }

    @Test
    void 대상이_없으면_시작하지_않는다() {
        when(transcriptionService.findTranscriptionNotStartedIds(any())).thenReturn(List.of());

        scheduler.startUnstartedTranscriptions();

        verify(transcriptionService, never()).start(anyLong());
    }

    @Test
    void 화자_선택_기한_만료는_한_건이_실패해도_나머지를_계속_처리한다() {
        when(transcriptionService.findSpeakerSelectionExpiredRecordingIds(any())).thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("DB 오류")).when(transcriptionService).expireSpeakerSelection(1L);

        scheduler.expireSpeakerSelections();

        verify(transcriptionService).expireSpeakerSelection(2L);
    }
}
