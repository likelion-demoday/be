package com.example.resay.domain.recording.service;

import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.global.exception.GeneralException;
import com.example.resay.global.infrastructure.audio.AudioDurationReader;
import com.example.resay.global.infrastructure.storage.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    void upload_재생시간이_범위를_벗어나면_저장_파일_삭제() {
        MockMultipartFile file = mp3File();
        when(localFileStorage.save(file, "mp3")).thenReturn(SAVED_PATH);
        when(audioDurationReader.readSeconds(SAVED_PATH)).thenReturn(60);

        GeneralException exception = assertThrows(GeneralException.class,
                () -> recordingService.upload(1L, file));

        assertEquals(RecordingErrorCode.INVALID_DURATION, exception.getErrorCode());
        verify(localFileStorage).delete(SAVED_PATH);
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
    void upload_성공하면_저장_파일을_지우지_않음() {
        MockMultipartFile file = mp3File();
        when(localFileStorage.save(file, "mp3")).thenReturn(SAVED_PATH);
        when(audioDurationReader.readSeconds(SAVED_PATH)).thenReturn(10 * 60);

        recordingService.upload(1L, file);

        verify(recordingRepository).save(any());
        verify(localFileStorage, never()).delete(any());
    }

    @Test
    void selectType_존재하지_않는_recordingId면_예외() {
        when(recordingRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(GeneralException.class,
                () -> recordingService.selectType(999L, 1L, null));

        assertEquals(RecordingErrorCode.RECORDING_NOT_FOUND, exception.getErrorCode());
    }

    private MockMultipartFile mp3File() {
        return new MockMultipartFile("audioFile", "test.mp3", "audio/mpeg", "내용".getBytes());
    }
}
