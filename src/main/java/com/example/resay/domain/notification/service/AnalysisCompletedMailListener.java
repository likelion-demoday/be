package com.example.resay.domain.notification.service;

import com.example.resay.domain.analysis.event.AnalysisCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 분석 결과 저장이 커밋된 뒤 다른 스레드에서 메일을 보낸다.
// 메일이 실패해도 분석 결과 · 녹음 상태에는 영향이 없다
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisCompletedMailListener {

    private final AnalysisCompletedMailService analysisCompletedMailService;

    @Async("notificationMailTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompleted(AnalysisCompletedEvent event) {
        try {
            analysisCompletedMailService.send(event.recordingId());
        } catch (RuntimeException exception) {
            log.error("분석 완료 메일 처리 실패: recordingId={}", event.recordingId(), exception);
        }
    }
}
