package com.example.resay.domain.recording.service;

import com.example.resay.domain.recording.repository.RecordingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordingRetentionServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 12, 0);

    @Mock
    private RecordingRepository recordingRepository;

    @Mock
    private RecordingService recordingService;

    @InjectMocks
    private RecordingRetentionService recordingRetentionService;

    @Test
    void deleteExpiredAudio_완료_실패_후_3일이_지난_음성을_지운다() {
        when(recordingRepository.findAudioExpiredIds(NOW.minusDays(3))).thenReturn(List.of(1L, 2L));
        when(recordingService.deleteAudio(1L)).thenReturn(true);
        when(recordingService.deleteAudio(2L)).thenReturn(true);

        int deleted = recordingRetentionService.deleteExpiredAudio(NOW);

        assertThat(deleted).isEqualTo(2);
    }

    @Test
    void deleteAbandonedRecordings_업로드_3시간이_지난_결제_전_녹음을_지운다() {
        when(recordingRepository.findIdsByStatusInAndCreatedAtBefore(
                RecordingService.UNPAID_STATUSES, NOW.minusHours(3))).thenReturn(List.of(1L));
        when(recordingService.deleteAbandoned(1L)).thenReturn(true);

        int deleted = recordingRetentionService.deleteAbandonedRecordings(NOW);

        assertThat(deleted).isEqualTo(1);
    }

    @Test
    void 한_건이_실패해도_나머지는_계속_지운다() {
        when(recordingRepository.findAudioExpiredIds(NOW.minusDays(3))).thenReturn(List.of(1L, 2L, 3L));
        when(recordingService.deleteAudio(1L)).thenThrow(new IllegalStateException("DB 오류"));
        when(recordingService.deleteAudio(2L)).thenReturn(false); // 파일 삭제 실패 → 다음 실행에서 재시도
        when(recordingService.deleteAudio(3L)).thenReturn(true);

        int deleted = recordingRetentionService.deleteExpiredAudio(NOW);

        assertThat(deleted).isEqualTo(1);
        verify(recordingService).deleteAudio(3L);
    }
}
