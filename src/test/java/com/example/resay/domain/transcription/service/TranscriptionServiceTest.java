package com.example.resay.domain.transcription.service;

import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.service.RecordingService;
import com.example.resay.domain.transcription.entity.Transcription;
import com.example.resay.domain.transcription.entity.TranscriptionProvider;
import com.example.resay.domain.transcription.port.SpeechRecognitionClient;
import com.example.resay.domain.transcription.repository.TranscriptionRepository;
import com.example.resay.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranscriptionServiceTest {

    private static final Long RECORDING_ID = 1L;
    private static final String AUDIO_PATH = "storage/recordings/test.m4a";

    @Mock
    private RecordingService recordingService;

    @Mock
    private SpeechRecognitionClient speechRecognitionClient;

    @Mock
    private TranscriptionRepository transcriptionRepository;

    @InjectMocks
    private TranscriptionService transcriptionService;

    @Test
    void start_callback_비밀값을_먼저_저장한_뒤_요청하고_작업_토큰을_기록() {
        List<String> savedTokens = new ArrayList<>(); // 저장 시점마다 토큰 값을 기록
        when(recordingService.startTranscribing(RECORDING_ID)).thenReturn(AUDIO_PATH);
        when(speechRecognitionClient.provider()).thenReturn(TranscriptionProvider.CLOVA_SPEECH);
        when(transcriptionRepository.save(any())).thenAnswer(invocation -> {
            Transcription saved = invocation.getArgument(0);
            savedTokens.add(saved.getJobToken());
            return saved;
        });
        when(speechRecognitionClient.requestRecognition(eq(AUDIO_PATH), anyString())).thenReturn("job-token");

        transcriptionService.start(RECORDING_ID);

        // 요청 전 저장(토큰 없음) → 요청 → 토큰 기록 순서
        InOrder order = inOrder(transcriptionRepository, speechRecognitionClient);
        order.verify(transcriptionRepository).save(any());
        order.verify(speechRecognitionClient).requestRecognition(eq(AUDIO_PATH), anyString());
        order.verify(transcriptionRepository).save(any());
        assertThat(savedTokens).containsExactly(null, "job-token");
        verify(recordingService, never()).fail(anyLong());
    }

    @Test
    void start_저장한_비밀값을_callback_요청에_그대로_사용() {
        List<Transcription> saved = new ArrayList<>();
        when(recordingService.startTranscribing(RECORDING_ID)).thenReturn(AUDIO_PATH);
        when(speechRecognitionClient.provider()).thenReturn(TranscriptionProvider.CLOVA_SPEECH);
        when(transcriptionRepository.save(any())).thenAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(speechRecognitionClient.requestRecognition(eq(AUDIO_PATH), anyString())).thenReturn("job-token");

        transcriptionService.start(RECORDING_ID);

        verify(speechRecognitionClient).requestRecognition(AUDIO_PATH, saved.get(0).getCallbackSecret());
    }

    @Test
    void start_결제완료가_아니면_요청하지_않음() {
        when(recordingService.startTranscribing(RECORDING_ID))
                .thenThrow(new GeneralException(RecordingErrorCode.INVALID_STATUS_TRANSITION));

        transcriptionService.start(RECORDING_ID);

        verify(speechRecognitionClient, never()).requestRecognition(anyString(), anyString());
        verify(transcriptionRepository, never()).save(any());
        verify(recordingService, never()).fail(anyLong()); // 다른 흐름에 있는 녹음 상태를 건드리지 않는다
    }

    @Test
    void start_외부_요청이_실패하면_FAILED_처리() {
        when(recordingService.startTranscribing(RECORDING_ID)).thenReturn(AUDIO_PATH);
        when(speechRecognitionClient.provider()).thenReturn(TranscriptionProvider.CLOVA_SPEECH);
        when(transcriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(speechRecognitionClient.requestRecognition(eq(AUDIO_PATH), anyString()))
                .thenThrow(new IllegalStateException("CLOVA 오류"));

        transcriptionService.start(RECORDING_ID);

        verify(recordingService).fail(RECORDING_ID);
    }

    @Test
    void start_요청_전_저장이_실패하면_요청하지_않고_FAILED_처리() {
        when(recordingService.startTranscribing(RECORDING_ID)).thenReturn(AUDIO_PATH);
        when(speechRecognitionClient.provider()).thenReturn(TranscriptionProvider.CLOVA_SPEECH);
        when(transcriptionRepository.save(any())).thenThrow(new IllegalStateException("DB 오류"));

        transcriptionService.start(RECORDING_ID);

        verify(speechRecognitionClient, never()).requestRecognition(anyString(), anyString());
        verify(recordingService).fail(RECORDING_ID);
    }

    @Test
    void start_토큰_기록이_실패하면_FAILED_처리() {
        when(recordingService.startTranscribing(RECORDING_ID)).thenReturn(AUDIO_PATH);
        when(speechRecognitionClient.provider()).thenReturn(TranscriptionProvider.CLOVA_SPEECH);
        when(transcriptionRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0))
                .thenThrow(new IllegalStateException("DB 오류"));
        when(speechRecognitionClient.requestRecognition(eq(AUDIO_PATH), anyString())).thenReturn("job-token");

        transcriptionService.start(RECORDING_ID);

        verify(recordingService).fail(RECORDING_ID);
    }
}
