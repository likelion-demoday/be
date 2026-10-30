package com.example.resay.domain.recording.service;

import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingFailureReason;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.event.RecordingFailedEvent;
import com.example.resay.domain.recording.event.RecordingPaymentCompletedEvent;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.global.exception.GeneralException;
import com.example.resay.global.infrastructure.audio.AudioDurationReader;
import com.example.resay.global.infrastructure.storage.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordingServiceTest {

    private static final String SAVED_PATH = "storage/recordings/saved.mp3";

    @Mock
    private RecordingRepository recordingRepository;

    @Mock
    private LocalFileStorage localFileStorage;

    @Mock
    private AudioDurationReader audioDurationReader;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private EntityManager entityManager;

    @Test
    void completePayment_조건부로_결제_완료로_바꾸고_전사_시작용_이벤트를_발행() {
        when(recordingRepository.markPaymentCompleted(eq(10L), eq(1L), any())).thenReturn(1);

        recordingService.completePayment(10L, 1L);

        verify(eventPublisher).publishEvent(new RecordingPaymentCompletedEvent(10L));
    }

    @Test
    void completePayment_이미_결제됐거나_유형_선택_전이면_400_이벤트도_없음() {
        when(recordingRepository.markPaymentCompleted(eq(10L), eq(1L), any())).thenReturn(0);
        when(recordingRepository.findByIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(Recording.create(1L, SAVED_PATH, 600)));

        GeneralException exception = assertThrows(GeneralException.class,
                () -> recordingService.completePayment(10L, 1L));

        assertEquals(RecordingErrorCode.INVALID_STATUS_TRANSITION, exception.getErrorCode());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void completePayment_다른_사람의_녹음이면_404_이벤트도_없음() {
        when(recordingRepository.markPaymentCompleted(eq(10L), eq(2L), any())).thenReturn(0);
        when(recordingRepository.findByIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(GeneralException.class,
                () -> recordingService.completePayment(10L, 2L));

        assertEquals(RecordingErrorCode.RECORDING_NOT_FOUND, exception.getErrorCode());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @InjectMocks
    private RecordingService recordingService;

    @Test
    void upload_허용되지_않는_확장자면_예외() {
        MockMultipartFile file = new MockMultipartFile(
                "audioFile", "test.txt", "text/plain", "내용".getBytes());

        assertThrows(GeneralException.class,
                () -> recordingService.upload(1L, file));

        verify(localFileStorage, never()).save(any(), any()); // 인자 2개짜리로 수정
    }

    @Test
    void upload_wav면_저장_전에_거부() {
        MockMultipartFile file = new MockMultipartFile(
                "audioFile", "test.wav", "audio/wav", "내용".getBytes());

        GeneralException exception = assertThrows(GeneralException.class,
                () -> recordingService.upload(1L, file));

        assertEquals(RecordingErrorCode.UNSUPPORTED_MEDIA_TYPE, exception.getErrorCode());
        verify(localFileStorage, never()).save(any(), any());
    }

    @Test
    void upload_용량초과면_예외() {
        byte[] 큰파일 = new byte[201 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "audioFile", "test.mp3", "audio/mpeg", 큰파일);

        assertThrows(GeneralException.class,
                () -> recordingService.upload(1L, file));

        verify(localFileStorage, never()).save(any(), any()); // 인자 2개짜리로 수정
    }

    @Test
    void upload_재생시간을_읽지_못하면_저장_파일_삭제() {
        MockMultipartFile file = mp3File();
        when(localFileStorage.save(file, "mp3")).thenReturn(SAVED_PATH);
        when(audioDurationReader.readSeconds(SAVED_PATH))
                .thenThrow(new GeneralException(RecordingErrorCode.UNSUPPORTED_MEDIA_TYPE));

        GeneralException exception = assertThrows(GeneralException.class,
                () -> recordingService.upload(1L, file));

        assertEquals(RecordingErrorCode.UNSUPPORTED_MEDIA_TYPE, exception.getErrorCode());
        verify(localFileStorage).delete(SAVED_PATH);
        verify(recordingRepository, never()).save(any());
    }

    @Test
    void upload_5분_미만이면_너무_짧음_오류로_응답하고_저장_파일_삭제() {
        MockMultipartFile file = mp3File();
        when(localFileStorage.save(file, "mp3")).thenReturn(SAVED_PATH);
        when(audioDurationReader.readSeconds(SAVED_PATH)).thenReturn(5 * 60 - 1);

        GeneralException exception = assertThrows(GeneralException.class,
                () -> recordingService.upload(1L, file));

        assertEquals(RecordingErrorCode.DURATION_TOO_SHORT, exception.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getErrorCode().getHttpStatus());
        verify(localFileStorage).delete(SAVED_PATH);
    }

    @Test
    void upload_30분_초과면_너무_김_오류로_응답하고_저장_파일_삭제() {
        MockMultipartFile file = mp3File();
        when(localFileStorage.save(file, "mp3")).thenReturn(SAVED_PATH);
        when(audioDurationReader.readSeconds(SAVED_PATH)).thenReturn(30 * 60 + 1);

        GeneralException exception = assertThrows(GeneralException.class,
                () -> recordingService.upload(1L, file));

        assertEquals(RecordingErrorCode.DURATION_TOO_LONG, exception.getErrorCode());
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, exception.getErrorCode().getHttpStatus());
        verify(localFileStorage).delete(SAVED_PATH);
    }

    @Test
    void upload_정확히_5분과_30분은_허용() {
        MockMultipartFile file = mp3File();
        when(localFileStorage.save(file, "mp3")).thenReturn(SAVED_PATH);
        when(audioDurationReader.readSeconds(SAVED_PATH)).thenReturn(5 * 60, 30 * 60);

        recordingService.upload(1L, file);
        recordingService.upload(1L, file);

        verify(localFileStorage, never()).delete(any());
    }

    @Test
    void upload_DB_저장이_실패하면_저장_파일_삭제() {
        MockMultipartFile file = mp3File();
        when(localFileStorage.save(file, "mp3")).thenReturn(SAVED_PATH);
        when(audioDurationReader.readSeconds(SAVED_PATH)).thenReturn(10 * 60);
        when(recordingRepository.save(any())).thenThrow(new DataIntegrityViolationException("DB 오류"));

        assertThrows(DataIntegrityViolationException.class,
                () -> recordingService.upload(1L, file));

        verify(localFileStorage).delete(SAVED_PATH);
    }

    @Test
    void upload_성공하면_재생시간을_저장하고_파일을_지우지_않음() {
        MockMultipartFile file = mp3File();
        when(localFileStorage.save(file, "mp3")).thenReturn(SAVED_PATH);
        when(audioDurationReader.readSeconds(SAVED_PATH)).thenReturn(13 * 60 + 3);

        recordingService.upload(1L, file);

        ArgumentCaptor<Recording> captor = ArgumentCaptor.forClass(Recording.class);
        verify(recordingRepository).save(captor.capture());
        assertEquals(13 * 60 + 3, captor.getValue().getDurationSeconds());
        verify(localFileStorage, never()).delete(any());
    }

    @Test
    void selectType_존재하지_않는_recordingId면_예외() {
        when(recordingRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(GeneralException.class,
                () -> recordingService.selectType(999L, 1L, null));

        assertEquals(RecordingErrorCode.RECORDING_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void failTranscription_전사_중이면_사유와_함께_실패로_바꾼다() {
        Recording recording = savedRecording(RecordingStatus.TRANSCRIBING);

        assertTrue(recordingService.failTranscription(1L, RecordingFailureReason.SPEAKER_NOT_SEPARATED));
        assertEquals(RecordingStatus.FAILED, recording.getStatus());
        assertEquals(RecordingFailureReason.SPEAKER_NOT_SEPARATED, recording.getFailureReason());
        // 크레딧 환급용 실패 이벤트
        verify(eventPublisher).publishEvent(
                new RecordingFailedEvent(1L, RecordingFailureReason.SPEAKER_NOT_SEPARATED));
    }

    @Test
    void failTranscription_전사_중이_아니면_건드리지_않는다() {
        Recording recording = savedRecording(RecordingStatus.ANALYZING);

        assertFalse(recordingService.failTranscription(1L, RecordingFailureReason.TRANSCRIPTION_TIMEOUT));
        assertEquals(RecordingStatus.ANALYZING, recording.getStatus());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void failAnalysis_분석_중이면_분석_실패로_바꾼다() {
        Recording recording = savedRecording(RecordingStatus.ANALYZING);

        assertTrue(recordingService.failAnalysis(1L, RecordingFailureReason.INSUFFICIENT_SPEAKER_DATA));
        assertEquals(RecordingStatus.FAILED, recording.getStatus());
        assertEquals(RecordingFailureReason.INSUFFICIENT_SPEAKER_DATA, recording.getFailureReason());
        verify(eventPublisher).publishEvent(
                new RecordingFailedEvent(1L, RecordingFailureReason.INSUFFICIENT_SPEAKER_DATA));
    }

    @Test
    void failAnalysis_분석_중이_아니면_건드리지_않고_이벤트도_없다() {
        Recording recording = savedRecording(RecordingStatus.COMPLETED);

        assertFalse(recordingService.failAnalysis(1L, RecordingFailureReason.ANALYSIS_FAILED));
        assertEquals(RecordingStatus.COMPLETED, recording.getStatus());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void completeAnalysis_분석_중이_아니거나_녹음이_없으면_건드리지_않는다() {
        Recording recording = savedRecording(RecordingStatus.FAILED);
        when(recordingRepository.findById(2L)).thenReturn(Optional.empty());

        assertFalse(recordingService.completeAnalysis(1L));
        assertFalse(recordingService.completeAnalysis(2L));
        assertEquals(RecordingStatus.FAILED, recording.getStatus());
    }

    @Test
    void deleteAudio_파일을_지우고_삭제_시각을_기록() {
        Recording recording = savedRecording(RecordingStatus.COMPLETED);
        when(localFileStorage.delete(SAVED_PATH)).thenReturn(true);

        boolean deleted = recordingService.deleteAudio(1L);

        assertTrue(deleted);
        assertFalse(recording.hasAudio());
        verify(recordingRepository, never()).delete(any()); // 녹음 기록은 남긴다
    }

    @Test
    void deleteAudio_파일_삭제에_실패하면_기록하지_않아_다음에_다시_시도() {
        Recording recording = savedRecording(RecordingStatus.COMPLETED);
        when(localFileStorage.delete(SAVED_PATH)).thenReturn(false);

        boolean deleted = recordingService.deleteAudio(1L);

        assertFalse(deleted);
        assertTrue(recording.hasAudio());
    }

    @Test
    void deleteAudio_이미_지운_음성이면_파일을_다시_건드리지_않음() {
        Recording recording = savedRecording(RecordingStatus.COMPLETED);
        recording.markAudioDeleted();

        assertTrue(recordingService.deleteAudio(1L));
        verify(localFileStorage, never()).delete(any());
    }

    @Test
    void deleteAbandoned_결제_전_녹음은_파일과_기록을_모두_삭제() {
        Recording recording = lockedRecording(RecordingStatus.TYPE_SELECTED);
        when(localFileStorage.delete(SAVED_PATH)).thenReturn(true);

        assertTrue(recordingService.deleteAbandoned(1L));
        verify(recordingRepository).delete(recording);
    }

    @Test
    void deleteAbandoned_그사이_결제됐으면_지우지_않음() {
        lockedRecording(RecordingStatus.PAYMENT_COMPLETED);

        assertFalse(recordingService.deleteAbandoned(1L));
        verify(localFileStorage, never()).delete(any());
        verify(recordingRepository, never()).delete(any());
    }

    @Test
    void deleteAbandoned_파일_삭제에_실패하면_기록도_남겨_다음에_다시_시도() {
        lockedRecording(RecordingStatus.UPLOADED);
        when(localFileStorage.delete(SAVED_PATH)).thenReturn(false);

        assertFalse(recordingService.deleteAbandoned(1L));
        verify(recordingRepository, never()).delete(any());
    }

    private Recording savedRecording(RecordingStatus status) {
        Recording recording = Recording.create(1L, SAVED_PATH, 600);
        ReflectionTestUtils.setField(recording, "status", status);
        when(recordingRepository.findById(1L)).thenReturn(Optional.of(recording));
        return recording;
    }

    // 결제와 동시에 일어날 수 있는 처리는 잠금 조회로 읽는다
    private Recording lockedRecording(RecordingStatus status) {
        Recording recording = Recording.create(1L, SAVED_PATH, 600);
        ReflectionTestUtils.setField(recording, "status", status);
        when(recordingRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(recording));
        return recording;
    }

    private MockMultipartFile mp3File() {
        return new MockMultipartFile("audioFile", "test.mp3", "audio/mpeg", "내용".getBytes());
    }
}
