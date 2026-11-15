package com.example.resay.domain.recording.scheduler;

import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.recording.service.RecordingSourceCleanupService;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordingSourceCleanupSchedulerTest {

    @Mock
    private RecordingRepository recordingRepository;

    @Mock
    private RecordingSourceCleanupService cleanupService;

    @InjectMocks
    private RecordingSourceCleanupScheduler scheduler;

    @Test
    void 한_건의_정리가_실패해도_나머지를_계속_처리한다() {
        var terminalStatuses = EnumSet.of(RecordingStatus.COMPLETED, RecordingStatus.FAILED);
        when(recordingRepository.findSourceCleanupPendingIds(
                terminalStatuses,
                PageRequest.of(0, RecordingSourceCleanupScheduler.BATCH_SIZE)
        ))
                .thenReturn(List.of(1L, 2L, 3L));
        when(cleanupService.deleteSourceData(1L)).thenThrow(new IllegalStateException("DB 오류"));
        when(cleanupService.deleteSourceData(2L)).thenReturn(false);
        when(cleanupService.deleteSourceData(3L)).thenReturn(true);

        scheduler.retryPendingCleanup();

        verify(cleanupService).deleteSourceData(1L);
        verify(cleanupService).deleteSourceData(2L);
        verify(cleanupService).deleteSourceData(3L);
    }
}
