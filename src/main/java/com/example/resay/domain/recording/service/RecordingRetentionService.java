package com.example.resay.domain.recording.service;

import com.example.resay.domain.recording.repository.RecordingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.LongPredicate;

// 개인정보처리방침의 음성 보관 기간을 지키기 위해 기한이 지난 음성을 정리한다
// 녹음마다 별도 트랜잭션으로 처리해 한 건이 실패해도 나머지는 계속 지운다
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingRetentionService {

    // 분석 완료·실패 후 음성 보관 기간
    static final Duration AUDIO_RETENTION = Duration.ofDays(3);
    // 결제하지 않은 녹음 보관 기간
    static final Duration UNPAID_RETENTION = Duration.ofHours(3);

    private final RecordingRepository recordingRepository;
    private final RecordingService recordingService;

    public int deleteExpiredAudio(LocalDateTime now) {
        List<Long> ids = recordingRepository.findAudioExpiredIds(now.minus(AUDIO_RETENTION));
        return deleteEach(ids, recordingService::deleteAudio, "보관 기간이 지난 음성");
    }

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
