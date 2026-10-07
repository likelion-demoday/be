package com.example.resay.domain.credit.service;

import com.example.resay.domain.credit.entity.UsagePurpose;
import com.example.resay.domain.recording.event.RecordingFailedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 결제한 녹음이 전사 · 화자 선택 · 분석 단계에서 실패하면 크레딧을 돌려준다.
 * 실패 사유와 무관하게 전액 환급한다. 사유는 환급 기록에 남기는 용도로만 쓴다.
 */
@Slf4j
@Component
public class AnalysisRefundListener {

    private final CreditService creditService;
    private final TransactionTemplate newTransaction;

    public AnalysisRefundListener(CreditService creditService, PlatformTransactionManager transactionManager) {
        this.creditService = creditService;
        // 실패가 커밋된 직후에 실행되므로 앞선 트랜잭션에 합류할 수 없다. 환급은 새 트랜잭션에서 한다
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecordingFailed(RecordingFailedEvent event) {
        try {
            newTransaction.executeWithoutResult(status -> creditService.refund(
                    UsagePurpose.ANALYSIS, event.recordingId(), event.failureReason().name()));
        } catch (RuntimeException exception) {
            // 여기서 실패해도 녹음의 실패 처리는 이미 끝났다. 환급은 정리 작업(CreditRefundReconciler)이 다시 시도한다
            log.error("분석 실패 환급에 실패했습니다. recordingId={}", event.recordingId(), exception);
        }
    }
}
