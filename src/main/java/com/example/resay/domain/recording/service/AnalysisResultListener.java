package com.example.resay.domain.recording.service;

import com.example.resay.domain.analysis.event.AnalysisCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 분석 결과를 녹음 상태에 반영한다 (완료 시각이 음성 3일 보관의 기준이 된다)
// 분석 저장이 커밋된 뒤 별도 트랜잭션으로 처리해, 여기서 실패해도 분석 결과는 남는다
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisResultListener {

    private final RecordingService recordingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCompleted(AnalysisCompletedEvent event) {
        if (!recordingService.completeAnalysis(event.recordingId())) {
            log.info("분석 완료를 반영할 녹음이 없습니다. recordingId={}", event.recordingId());
        }
    }
}
