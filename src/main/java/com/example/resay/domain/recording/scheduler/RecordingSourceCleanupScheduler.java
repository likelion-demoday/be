package com.example.resay.domain.recording.scheduler;

import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.recording.service.RecordingSourceCleanupService;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 완료·실패 이벤트를 처리하지 못한 경우와 정책 변경 전에 남은 원본 데이터를 정리한다.
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordingSourceCleanupScheduler {

    private static final Set<RecordingStatus> TERMINAL_STATUSES =
            EnumSet.of(RecordingStatus.COMPLETED, RecordingStatus.FAILED);

    private final RecordingRepository recordingRepository;
    private final RecordingSourceCleanupService cleanupService;

    @Scheduled(cron = "${storage.source-cleanup.cron:0 */10 * * * *}", zone = "Asia/Seoul")
    public void retryPendingCleanup() {
        List<Long> ids = recordingRepository.findSourceCleanupPendingIds(TERMINAL_STATUSES);
        int completed = 0;
        for (Long id : ids) {
            try {
                if (cleanupService.deleteSourceData(id)) {
                    completed++;
                }
            } catch (RuntimeException exception) {
                log.warn("원본 데이터 정리 재시도 실패: recordingId={}", id, exception);
            }
        }
        if (!ids.isEmpty()) {
            log.info("원본 데이터 정리 재시도: 대상 {}건, 완료 {}건", ids.size(), completed);
        }
    }
}
