package com.example.resay.domain.recording.service;

import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.global.exception.GeneralException;
import com.example.resay.global.infrastructure.storage.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordingServiceTest {

    @Mock
    private RecordingRepository recordingRepository;

    @Mock
    private LocalFileStorage localFileStorage;

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
    void upload_용량초과면_예외() {
        byte[] 큰파일 = new byte[201 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "audioFile", "test.mp3", "audio/mpeg", 큰파일);

        assertThrows(GeneralException.class,
                () -> recordingService.upload(1L, file));

        verify(localFileStorage, never()).save(any(), any()); // 인자 2개짜리로 수정
    }

    @Test
    void selectType_존재하지_않는_recordingId면_예외() {
        when(recordingRepository.findByIdAndUserId(999L, 1L)).thenReturn(Optional.empty());

        GeneralException exception = assertThrows(GeneralException.class,
                () -> recordingService.selectType(999L, 1L, null));

        org.junit.jupiter.api.Assertions.assertEquals(
                RecordingErrorCode.RECORDING_NOT_FOUND, exception.getErrorCode());
    }
}