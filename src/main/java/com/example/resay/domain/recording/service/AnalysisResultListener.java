package com.example.resay.domain.recording.service;

import com.example.resay.domain.analysis.event.AnalysisCompletedEvent;
import com.example.resay.domain.analysis.event.AnalysisFailedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 분석 결과를 녹음 상태에 반영한다
// 분석 저장이 커밋된 뒤 별도 트랜잭션으로 처리해, 여기서 실패해도 분석 결과는 남는다
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisResultListener {

    private final RecordingService recordingService;
    private final AnalysisFailureRecordingService analysisFailureRecordingService;

    // 녹음을 먼저 COMPLETED로 커밋한 뒤 후순위 원본 데이터 정리 리스너가 실행돼야 한다.
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCompleted(AnalysisCompletedEvent event) {
        if (!recordingService.completeAnalysis(event.recordingId())) {
            log.info("분석 완료를 반영할 녹음이 없습니다. recordingId={}", event.recordingId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFailed(AnalysisFailedEvent event) {
        if (!analysisFailureRecordingService.recordFailure(event.recordingId(), event.failureReason())) {
            log.info("분석 실패를 반영할 녹음이 없습니다. recordingId={}", event.recordingId());
        }
    }
}
