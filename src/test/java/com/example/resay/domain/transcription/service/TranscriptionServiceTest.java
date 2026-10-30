package com.example.resay.domain.transcription.service;

import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.entity.RecordingFailureReason;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.recording.service.RecordingService;
import com.example.resay.domain.transcription.code.TranscriptionErrorCode;
import com.example.resay.domain.transcription.entity.TranscriptSegment;
import com.example.resay.domain.transcription.entity.Transcription;
import com.example.resay.domain.transcription.entity.TranscriptionProvider;
import com.example.resay.domain.transcription.entity.TranscriptionStatus;
import com.example.resay.domain.transcription.model.RecognitionResult;
import com.example.resay.domain.transcription.model.RecognitionResult.RecognizedSegment;
import com.example.resay.domain.transcription.port.SpeechRecognitionClient;
import com.example.resay.domain.transcription.repository.TranscriptSegmentRepository;
import com.example.resay.domain.transcription.repository.TranscriptionRepository;
import com.example.resay.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
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
    private static final String RAW_BODY = "{...}";

    @Mock
    private RecordingService recordingService;

    @Mock
    private SpeechRecognitionClient speechRecognitionClient;

    @Mock
    private TranscriptionRepository transcriptionRepository;

    @Mock
    private TranscriptSegmentRepository transcriptSegmentRepository;

    @Mock
    private RecordingRepository recordingRepository;

    @InjectMocks
    private TranscriptionService transcriptionService;

    // ---------- 전사 요청 ----------

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
        verify(recordingService, never()).failTranscription(anyLong(), any());
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
        verify(recordingService, never()).failTranscription(anyLong(), any()); // 다른 흐름에 있는 녹음 상태를 건드리지 않는다
    }

    @Test
    void start_외부_요청이_실패하면_요청_실패로_처리() {
        when(recordingService.startTranscribing(RECORDING_ID)).thenReturn(AUDIO_PATH);
        when(speechRecognitionClient.provider()).thenReturn(TranscriptionProvider.CLOVA_SPEECH);
        when(transcriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(speechRecognitionClient.requestRecognition(eq(AUDIO_PATH), anyString()))
                .thenThrow(new IllegalStateException("CLOVA 오류"));

        transcriptionService.start(RECORDING_ID);

        verify(recordingService).failTranscription(RECORDING_ID, RecordingFailureReason.TRANSCRIPTION_REQUEST_FAILED);
    }

    @Test
    void start_요청_전_저장이_실패하면_요청하지_않고_요청_실패로_처리() {
        when(recordingService.startTranscribing(RECORDING_ID)).thenReturn(AUDIO_PATH);
        when(speechRecognitionClient.provider()).thenReturn(TranscriptionProvider.CLOVA_SPEECH);
        when(transcriptionRepository.save(any())).thenThrow(new IllegalStateException("DB 오류"));

        transcriptionService.start(RECORDING_ID);

        verify(speechRecognitionClient, never()).requestRecognition(anyString(), anyString());
        verify(recordingService).failTranscription(RECORDING_ID, RecordingFailureReason.TRANSCRIPTION_REQUEST_FAILED);
    }

    // ---------- 결과 수신 ----------

    @Test
    void receiveResult_두_화자의_구간을_시작_시각_순서로_저장하고_완료() {
        Transcription transcription = requested("job-token");
        when(speechRecognitionClient.parseResult(RAW_BODY)).thenReturn(new RecognitionResult(true, "job-token", List.of(
                new RecognizedSegment("2", 4510, 8100, "안녕하세요. 무엇을 도와드릴까요?"),
                new RecognizedSegment("1", 350, 2420, " 안녕 "),
                new RecognizedSegment("1", 9000, 9500, "   "), // 내용 없는 구간은 버린다
                new RecognizedSegment("2", 10000, 10000, "어") // 시각이 잘못된 구간은 버린다
        )));

        transcriptionService.receiveResult(transcription.getCallbackSecret(), RAW_BODY);

        List<TranscriptSegment> saved = captureSavedSegments();
        assertThat(saved).extracting(TranscriptSegment::getSegmentNo).containsExactly(1, 2);
        assertThat(saved).extracting(TranscriptSegment::getSpeakerLabel).containsExactly("1", "2");
        assertThat(saved.get(0).getContent()).isEqualTo("안녕");
        assertThat(saved.get(0).getStartMs()).isEqualTo(350L);
        assertThat(transcription.getStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
        verify(recordingService, never()).failTranscription(anyLong(), any());
    }

    @Test
    void receiveResult_화자가_한_명이면_화자_구분_실패로_처리() {
        Transcription transcription = requested("job-token");
        when(speechRecognitionClient.parseResult(RAW_BODY)).thenReturn(new RecognitionResult(true, "job-token", List.of(
                new RecognizedSegment("1", 0, 1000, "혼자 말하는 중"),
                new RecognizedSegment("1", 2000, 3000, "계속 혼자")
        )));

        transcriptionService.receiveResult(transcription.getCallbackSecret(), RAW_BODY);

        assertThat(transcription.getStatus()).isEqualTo(TranscriptionStatus.FAILED);
        verify(recordingService).failTranscription(RECORDING_ID, RecordingFailureReason.SPEAKER_NOT_SEPARATED);
        verify(transcriptSegmentRepository, never()).saveAll(anyList());
    }

    @Test
    void receiveResult_전사_실패_응답이면_전사_실패로_처리() {
        Transcription transcription = requested("job-token");
        when(speechRecognitionClient.parseResult(RAW_BODY)).thenReturn(new RecognitionResult(false, "job-token", List.of()));

        transcriptionService.receiveResult(transcription.getCallbackSecret(), RAW_BODY);

        assertThat(transcription.getStatus()).isEqualTo(TranscriptionStatus.FAILED);
        verify(recordingService).failTranscription(RECORDING_ID, RecordingFailureReason.TRANSCRIPTION_FAILED);
    }

    @Test
    void receiveResult_모르는_비밀값이면_404() {
        when(transcriptionRepository.findByCallbackSecret("unknown")).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(GeneralException.class,
                () -> transcriptionService.receiveResult("unknown", RAW_BODY));

        assertEquals(TranscriptionErrorCode.TRANSCRIPTION_NOT_FOUND, exception.getErrorCode());
        verify(speechRecognitionClient, never()).parseResult(anyString());
    }

    @Test
    void receiveResult_이미_처리한_결과가_다시_오면_무시() {
        Transcription transcription = requested("job-token");
        transcription.complete();

        transcriptionService.receiveResult(transcription.getCallbackSecret(), RAW_BODY);

        verify(speechRecognitionClient, never()).parseResult(anyString());
        verify(transcriptSegmentRepository, never()).saveAll(anyList());
    }

    @Test
    void receiveResult_작업_토큰이_다르면_거부() {
        Transcription transcription = requested("job-token");
        when(speechRecognitionClient.parseResult(RAW_BODY))
                .thenReturn(new RecognitionResult(true, "other-token", List.of()));

        assertThrows(GeneralException.class,
                () -> transcriptionService.receiveResult(transcription.getCallbackSecret(), RAW_BODY));

        assertThat(transcription.getStatus()).isEqualTo(TranscriptionStatus.REQUESTED);
        verify(transcriptSegmentRepository, never()).saveAll(anyList());
    }

    @Test
    void receiveResult_토큰_기록보다_callback이_먼저_오면_토큰을_함께_기록() {
        Transcription transcription = requested(null);
        when(speechRecognitionClient.parseResult(RAW_BODY)).thenReturn(new RecognitionResult(true, "job-token", List.of(
                new RecognizedSegment("1", 0, 1000, "안녕"),
                new RecognizedSegment("2", 1500, 2500, "반가워")
        )));

        transcriptionService.receiveResult(transcription.getCallbackSecret(), RAW_BODY);

        assertThat(transcription.getJobToken()).isEqualTo("job-token");
        assertThat(transcription.getStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
    }

    // ---------- 시간 초과 ----------

    @Test
    void findTimedOutRecordingIds_1시간_전을_기준으로_조회() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 5, 12, 0);
        when(transcriptionRepository.findTimedOutRecordingIds(now.minusHours(1))).thenReturn(List.of(RECORDING_ID));

        assertThat(transcriptionService.findTimedOutRecordingIds(now)).containsExactly(RECORDING_ID);
    }

    @Test
    void failTimedOut_전사_요청과_녹음을_시간_초과로_실패_처리() {
        Transcription transcription = Transcription.prepare(RECORDING_ID, TranscriptionProvider.CLOVA_SPEECH);
        when(transcriptionRepository.findByRecordingId(RECORDING_ID)).thenReturn(Optional.of(transcription));

        transcriptionService.failTimedOut(RECORDING_ID);

        assertThat(transcription.getStatus()).isEqualTo(TranscriptionStatus.FAILED); // 늦게 온 결과는 무시된다
        verify(recordingService).failTranscription(RECORDING_ID, RecordingFailureReason.TRANSCRIPTION_TIMEOUT);
    }

    @Test
    void failTimedOut_전사_요청_기록이_없어도_녹음은_실패_처리() {
        when(transcriptionRepository.findByRecordingId(RECORDING_ID)).thenReturn(Optional.empty());

        transcriptionService.failTimedOut(RECORDING_ID);

        verify(recordingService).failTranscription(RECORDING_ID, RecordingFailureReason.TRANSCRIPTION_TIMEOUT);
    }

    // ---------- 전사 시작 복구 ----------

    @Test
    void findTranscriptionNotStartedIds_결제_후_5분이_지난_녹음을_조회() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 6, 12, 0);
        when(recordingRepository.findTranscriptionNotStartedIds(now.minusMinutes(5))).thenReturn(List.of(RECORDING_ID));

        assertThat(transcriptionService.findTranscriptionNotStartedIds(now)).containsExactly(RECORDING_ID);
    }

    // ---------- 화자 선택 기한 만료 ----------

    @Test
    void findSpeakerSelectionExpiredRecordingIds_3일_전을_기준으로_조회() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 6, 12, 0);
        when(transcriptionRepository.findSpeakerSelectionExpiredRecordingIds(now.minusDays(3)))
                .thenReturn(List.of(RECORDING_ID));

        assertThat(transcriptionService.findSpeakerSelectionExpiredRecordingIds(now)).containsExactly(RECORDING_ID);
    }

    @Test
    void expireSpeakerSelection_실패로_바꾸고_음성을_바로_지운다() {
        when(recordingService.failTranscription(RECORDING_ID, RecordingFailureReason.SPEAKER_SELECTION_EXPIRED))
                .thenReturn(true);

        transcriptionService.expireSpeakerSelection(RECORDING_ID);

        verify(recordingService).deleteAudio(RECORDING_ID);
    }

    @Test
    void expireSpeakerSelection_그사이_화자를_골랐으면_건드리지_않는다() {
        when(recordingService.failTranscription(RECORDING_ID, RecordingFailureReason.SPEAKER_SELECTION_EXPIRED))
                .thenReturn(false);

        transcriptionService.expireSpeakerSelection(RECORDING_ID);

        verify(recordingService, never()).deleteAudio(anyLong());
    }

    private Transcription requested(String jobToken) {
        Transcription transcription = Transcription.prepare(RECORDING_ID, TranscriptionProvider.CLOVA_SPEECH);
        if (jobToken != null) {
            transcription.assignJobToken(jobToken);
        }
        when(transcriptionRepository.findByCallbackSecret(transcription.getCallbackSecret()))
                .thenReturn(Optional.of(transcription));
        return transcription;
    }

    @SuppressWarnings("unchecked")
    private List<TranscriptSegment> captureSavedSegments() {
        ArgumentCaptor<List<TranscriptSegment>> captor = ArgumentCaptor.forClass(List.class);
        verify(transcriptSegmentRepository).saveAll(captor.capture());
        return captor.getValue();
    }
}
