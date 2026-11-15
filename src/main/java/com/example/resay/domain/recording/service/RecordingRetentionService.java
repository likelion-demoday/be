package com.example.resay.domain.recording.service;

import com.example.resay.domain.recording.repository.RecordingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.LongPredicate;

// 결제 전에 이탈해 더 진행되지 않는 녹음과 파일을 정리한다.
// 분석 성공·실패 후 원본 정리는 RecordingSourceCleanupService가 담당한다.
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingRetentionService {

    // 결제하지 않은 녹음 보관 기간
    static final Duration UNPAID_RETENTION = Duration.ofHours(3);

    private final RecordingRepository recordingRepository;
    private final RecordingService recordingService;

    public int deleteAbandonedRecordings(LocalDateTime now) {
        List<Long> ids = recordingRepository.findIdsByStatusInAndCreatedAtBefore(
                RecordingService.UNPAID_STATUSES, now.minus(UNPAID_RETENTION));
        return deleteEach(ids, recordingService::deleteAbandoned, "결제하지 않은 녹음");
    }

    private int deleteEach(List<Long> ids, LongPredicate delete, String target) {
        int deleted = 0;
        for (Long id : ids) {
            try {
                if (delete.test(id)) {
                    deleted++;
                }
            } catch (RuntimeException e) {
                log.warn("{} 삭제에 실패했습니다. 다음 실행에서 다시 시도합니다. recordingId={}", target, id, e);
            }
        }
        if (!ids.isEmpty()) {
            log.info("{} 정리: 대상 {}건, 삭제 {}건", target, ids.size(), deleted);
        }
        return deleted;
    }
}
