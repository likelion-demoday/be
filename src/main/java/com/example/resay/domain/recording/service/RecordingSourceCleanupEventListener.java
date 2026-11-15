package com.example.resay.domain.recording.service;

import com.example.resay.domain.analysis.event.AnalysisCompletedEvent;
import com.example.resay.domain.recording.event.RecordingFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 분석 성공 또는 결제 후 처리의 최종 실패가 커밋된 뒤 원본 데이터를 즉시 지운다.
// 예외를 밖으로 전달하지 않아 다른 완료·실패 리스너(상태 반영, 환급, 알림)를 막지 않는다.
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordingSourceCleanupEventListener {

    private final RecordingSourceCleanupService cleanupService;

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnalysisCompleted(AnalysisCompletedEvent event) {
        cleanup(event.recordingId(), "분석 완료");
    }

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecordingFailed(RecordingFailedEvent event) {
        cleanup(event.recordingId(), "처리 실패");
    }

    private void cleanup(Long recordingId, String trigger) {
        try {
            if (!cleanupService.deleteSourceData(recordingId)) {
                log.warn(
                        "{} 후 원본 음성 삭제 실패. 정리 스케줄러에서 다시 시도합니다. recordingId={}",
                        trigger,
                        recordingId
                );
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "{} 후 원본 데이터 정리 실패. 정리 스케줄러에서 다시 시도합니다. recordingId={}",
                    trigger,
                    recordingId,
                    exception
            );
        }
    }
}
