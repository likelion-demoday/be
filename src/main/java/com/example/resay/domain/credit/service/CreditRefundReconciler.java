package com.example.resay.domain.credit.service;

import com.example.resay.domain.credit.entity.UsagePurpose;
import com.example.resay.domain.credit.repository.CreditUsageRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 환급이 누락된 분석 결제를 찾아 환급한다.
 * 녹음이 실패하면 이벤트로 바로 환급하지만(AnalysisRefundListener), 그 순간 서버가 꺼지거나 환급이 실패하면
 * "녹음은 실패인데 크레딧은 차감된 채"로 남는다. 이 작업이 그런 건을 주기적으로 찾아 맞춘다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditRefundReconciler {

    // 방금 실패해서 이벤트로 환급이 진행 중인 건과 겹치지 않게 조금 지난 건만 본다
    static final Duration GRACE_PERIOD = Duration.ofMinutes(5);

    private final CreditUsageRepository creditUsageRepository;
    private final CreditService creditService;

    @Scheduled(cron = "${credit.refund-reconcile.cron:0 */10 * * * *}", zone = "Asia/Seoul")
    public void reconcile() {
        refundFailedAnalyses(LocalDateTime.now());
    }

    int refundFailedAnalyses(LocalDateTime now) {
        List<Long> recordingIds = creditUsageRepository.findUnrefundedFailedAnalysisRecordingIds(
                UsagePurpose.ANALYSIS, now.minus(GRACE_PERIOD));
        int refunded = 0;
        for (Long recordingId : recordingIds) {
            try {
                if (creditService.refund(UsagePurpose.ANALYSIS, recordingId, "RECONCILED")) {
                    refunded++;
                }
            } catch (RuntimeException exception) {
                log.warn("누락된 환급 처리에 실패했습니다. 다음 실행에서 다시 시도합니다. recordingId={}", recordingId, exception);
            }
        }
        if (!recordingIds.isEmpty()) {
            log.info("누락된 분석 환급 정리: 대상 {}건, 환급 {}건", recordingIds.size(), refunded);
        }
        return refunded;
    }
}
