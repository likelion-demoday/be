package com.example.resay.domain.credit.service;

import com.example.resay.domain.credit.config.CreditProperties;
import com.example.resay.domain.credit.entity.CreditUsage;
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
 * 환급이 누락된 사용 건을 찾아 환급한다.
 *
 * 분석: 녹음이 실패하면 이벤트로 바로 환급하지만(AnalysisRefundListener), 그 순간 서버가 꺼지거나 환급이 실패하면
 * "녹음은 실패인데 크레딧은 차감된 채"로 남는다. 이 작업이 그런 건을 주기적으로 찾아 맞춘다.
 *
 * 캐릭터: 생성이 실패하면 생성 작업이 바로 환급하지만(CharacterImageGenerationListener), 생성 도중 서버가 재시작되면
 * 실패 처리 자체가 일어나지 않아 이미지가 만들어지지 않은 채 차감만 남는다. 요청 후 일정 시간 진행이 없으면 환급한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditRefundReconciler {

    // 방금 실패해서 이벤트로 환급이 진행 중인 건과 겹치지 않게 조금 지난 건만 본다
    static final Duration GRACE_PERIOD = Duration.ofMinutes(5);

    private final CreditUsageRepository creditUsageRepository;
    private final CreditService creditService;
    private final CreditProperties creditProperties;

    @Scheduled(cron = "${credit.refund-reconcile.cron:0 */10 * * * *}", zone = "Asia/Seoul")
    public void reconcile() {
        LocalDateTime now = LocalDateTime.now();
        refundFailedAnalyses(now);
        refundStalledCharacters(now);
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

    int refundStalledCharacters(LocalDateTime now) {
        List<CreditUsage> usages = creditUsageRepository.findStalledCharacterUsages(
                now.minus(creditProperties.characterStallTimeout()));
        int refunded = 0;
        for (CreditUsage usage : usages) {
            try {
                // 녹음이 아니라 사용 건을 지정해 환급한다. 조회한 뒤 이 건이 환급되고 사용자가 다시 요청했다면
                // 같은 녹음의 새 사용 건은 정상 생성 중이므로 건드리면 안 된다
                if (creditService.refundUsage(usage.getUserId(), usage.getId(), "RECONCILED")) {
                    refunded++;
                }
            } catch (RuntimeException exception) {
                log.warn("멈춘 캐릭터 생성 환급에 실패했습니다. 다음 실행에서 다시 시도합니다. usageId={}, recordingId={}",
                        usage.getId(), usage.getRecordingId(), exception);
            }
        }
        if (!usages.isEmpty()) {
            log.info("멈춘 캐릭터 생성 환급 정리: 대상 {}건, 환급 {}건", usages.size(), refunded);
        }
        return refunded;
    }
}
