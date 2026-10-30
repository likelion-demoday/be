package com.example.resay.domain.payment.service;

import com.example.resay.domain.payment.config.PaymentProperties;
import com.example.resay.domain.payment.entity.Payment;
import com.example.resay.domain.payment.entity.PaymentCancel;
import com.example.resay.domain.payment.entity.PaymentCancelStatus;
import com.example.resay.domain.payment.entity.PaymentStatus;
import com.example.resay.domain.payment.repository.PaymentCancelRepository;
import com.example.resay.domain.payment.repository.PaymentRepository;
import com.example.resay.global.infrastructure.nicepay.NicepayProperties;
import com.example.resay.global.infrastructure.nicepay.NicepayUnknownResultException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 끝나지 않은 채 남은 충전 주문을 정리한다.
 *
 *  - 승인을 요청했는데 결과가 확정되지 않은 주문(APPROVING): 승인 직후 서버가 꺼졌거나(배포 포함) 응답을 받지 못한 경우다.
 *    나이스페이에 조회해서 결제됐으면 크레딧을 지급하고, 결제되지 않았으면 실패로 끝낸다.
 *  - 환불(결제 취소)을 요청했는데 결과가 확정되지 않은 주문(CANCELLING): 취소 응답을 받지 못한 경우다.
 *    나이스페이에 조회해서 취소됐으면 환불로 끝내고, 취소되지 않았으면 회수한 크레딧을 되돌린다.
 *  - 결제하지 않은 주문(READY): 결제창을 닫은 경우다. 시간이 지나면 만료 처리한다.
 */
@Slf4j
@Component
public class PaymentReconciler {

    // 나이스페이가 응답하지 않을 때 한 번 실행에서 기다려 볼 횟수. 건마다 타임아웃까지 기다리면
    // 같은 스레드에서 도는 다른 정리 작업(전사 재시작, 환급 등)이 밀리므로, 넘으면 남은 주문은 다음 실행으로 넘긴다
    private static final int MAX_UNANSWERED_PER_RUN = 2;
    // 이 시간이 지나도 확정되지 않은 주문은 사람이 봐야 한다 (망취소도 더는 할 수 없는 시점)
    private static final Duration MANUAL_CHECK_AFTER = Duration.ofHours(1);

    private final PaymentRepository paymentRepository;
    private final PaymentCancelRepository paymentCancelRepository;
    private final PaymentApprovalService paymentApprovalService;
    private final PaymentCancelService paymentCancelService;
    private final PaymentProperties paymentProperties;
    private int runCount;

    public PaymentReconciler(
            PaymentRepository paymentRepository,
            PaymentCancelRepository paymentCancelRepository,
            PaymentApprovalService paymentApprovalService,
            PaymentCancelService paymentCancelService,
            PaymentProperties paymentProperties,
            NicepayProperties nicepayProperties
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentCancelRepository = paymentCancelRepository;
        this.paymentApprovalService = paymentApprovalService;
        this.paymentCancelService = paymentCancelService;
        this.paymentProperties = paymentProperties;
        requireGracePeriodLongerThanApproval(paymentProperties, nicepayProperties);
    }

    // 멈춘 주문의 사용자는 그동안 "결제 확인 중"으로 보게 되므로 자주 돈다 (대상이 없으면 가벼운 조회 몇 번으로 끝난다)
    @Scheduled(cron = "${payment.reconcile.cron:0 * * * * *}", zone = "Asia/Seoul")
    public void reconcile() {
        LocalDateTime now = LocalDateTime.now();
        settleStuckApprovals(now);
        settleStuckCancels(now);
        expireAbandonedOrders(now);
    }

    void settleStuckApprovals(LocalDateTime now) {
        // 방금 승인을 요청해 아직 처리 중인 주문과 겹치지 않게 조금 지난 주문만 본다
        List<Payment> stuck = rotated(paymentRepository.findByStatusAndApprovalRequestedAtBeforeOrderByIdAsc(
                PaymentStatus.APPROVING, now.minus(paymentProperties.approvalGracePeriod())));
        int unanswered = 0;
        for (Payment payment : stuck) {
            try {
                paymentApprovalService.settleStuckApproval(payment);
            } catch (NicepayUnknownResultException exception) {
                log.warn("승인 중인 주문을 조회하지 못했습니다. 다음 실행에서 다시 시도합니다: orderId={}",
                        payment.getOrderId());
                if (++unanswered >= MAX_UNANSWERED_PER_RUN) {
                    break;
                }
            } catch (RuntimeException exception) {
                // 한 주문의 문제로 나머지 주문이 막히지 않게 한다
                log.warn("승인 중인 주문을 정리하지 못했습니다. 다음 실행에서 다시 시도합니다: orderId={}",
                        payment.getOrderId(), exception);
            }
        }

        long overdue = paymentRepository.countByStatusAndApprovalRequestedAtBefore(
                PaymentStatus.APPROVING, now.minus(MANUAL_CHECK_AFTER));
        if (overdue > 0) {
            log.error("결제 여부가 1시간 넘게 확인되지 않은 충전 주문이 {}건 있습니다. 나이스페이 관리자에서 확인이 필요합니다.", overdue);
        }
    }

    void settleStuckCancels(LocalDateTime now) {
        // 방금 취소를 요청해 아직 처리 중인 건과 겹치지 않게 조금 지난 건만 본다.
        // 취소 요청은 취소 1번 + 조회 1번이라 승인(승인 1번 + 망취소 1번 + 재요청 대기)보다 오래 걸리지 않으므로 같은 대기 시간을 쓴다
        List<PaymentCancel> stuck = rotated(paymentCancelRepository.findByStatusAndCreatedAtBeforeOrderByIdAsc(
                PaymentCancelStatus.REQUESTED, now.minus(paymentProperties.approvalGracePeriod())));
        int unanswered = 0;
        for (PaymentCancel cancel : stuck) {
            try {
                paymentCancelService.settleStuckCancel(cancel);
            } catch (NicepayUnknownResultException exception) {
                log.warn("취소 중인 주문을 조회하지 못했습니다. 다음 실행에서 다시 시도합니다: paymentId={}",
                        cancel.getPaymentId());
                if (++unanswered >= MAX_UNANSWERED_PER_RUN) {
                    break;
                }
            } catch (RuntimeException exception) {
                log.warn("취소 중인 주문을 정리하지 못했습니다. 다음 실행에서 다시 시도합니다: paymentId={}",
                        cancel.getPaymentId(), exception);
            }
        }

        // 크레딧은 회수된 채로 사용자의 환불이 멈춰 있는 상태다
        long overdue = paymentCancelRepository.countByStatusAndCreatedAtBefore(
                PaymentCancelStatus.REQUESTED, now.minus(MANUAL_CHECK_AFTER));
        if (overdue > 0) {
            log.error("환불(결제 취소) 여부가 1시간 넘게 확인되지 않은 충전 주문이 {}건 있습니다. 나이스페이 관리자에서 확인이 필요합니다.", overdue);
        }
    }

    int expireAbandonedOrders(LocalDateTime now) {
        int expired = 0;
        for (Long paymentId : paymentRepository.findExpiredReadyIds(now.minus(paymentProperties.orderExpiration()))) {
            // 그사이 인증 결과가 도착해 결제가 진행 중이면 0건이 바뀐다
            expired += paymentRepository.markExpired(paymentId, now);
        }
        if (expired > 0) {
            log.info("결제하지 않은 충전 주문 만료: {}건", expired);
        }
        return expired;
    }

    // 응답이 오지 않는 건이 매번 맨 앞에서 나머지를 막지 않도록, 실행할 때마다 시작 위치를 한 칸씩 옮긴다
    private <T> List<T> rotated(List<T> stuck) {
        if (stuck.isEmpty()) {
            return stuck;
        }
        List<T> rotated = new ArrayList<>(stuck);
        Collections.rotate(rotated, -(runCount++ % rotated.size()));
        return rotated;
    }

    // 승인(또는 망취소) 요청이 아직 진행 중인 주문을 정리 작업이 "결제되지 않음"으로 끝내면, 그 직후 도착한 결제 완료 응답이
    // 갈 곳이 없어진다. 설정을 잘못 바꿔 그런 일이 생기지 않도록 서버가 뜰 때 확인한다
    private static void requireGracePeriodLongerThanApproval(
            PaymentProperties paymentProperties, NicepayProperties nicepayProperties
    ) {
        if (nicepayProperties.connectTimeout() == null || nicepayProperties.readTimeout() == null) {
            return;
        }
        // 승인 1번 + 망취소 1번 + 승인을 다시 요청하기 전에 기다리는 시간
        Duration slowestApproval = nicepayProperties.connectTimeout().plus(nicepayProperties.readTimeout()).multipliedBy(2);
        for (Duration retryDelay : paymentProperties.approvalRetryDelays()) {
            slowestApproval = slowestApproval.plus(retryDelay);
        }
        if (paymentProperties.approvalGracePeriod().compareTo(slowestApproval) <= 0) {
            throw new IllegalStateException(
                    "payment.approval-grace-period(%s)는 승인에 걸릴 수 있는 시간(%s: 타임아웃 합의 두 배 + 재요청 대기)보다 길어야 합니다."
                            .formatted(paymentProperties.approvalGracePeriod(), slowestApproval));
        }
    }
}
