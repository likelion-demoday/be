package com.example.resay.domain.payment.service;

import com.example.resay.domain.credit.service.CreditService;
import com.example.resay.domain.payment.code.PaymentErrorCode;
import com.example.resay.domain.payment.dto.PaymentCancelResponseDto;
import com.example.resay.domain.payment.entity.Payment;
import com.example.resay.domain.payment.entity.PaymentCancel;
import com.example.resay.domain.payment.entity.PaymentCancelStatus;
import com.example.resay.domain.payment.entity.PaymentStatus;
import com.example.resay.domain.payment.repository.PaymentCancelRepository;
import com.example.resay.domain.payment.repository.PaymentRepository;
import com.example.resay.global.exception.GeneralException;
import com.example.resay.global.infrastructure.nicepay.NicepayClient;
import com.example.resay.global.infrastructure.nicepay.NicepayProperties;
import com.example.resay.global.infrastructure.nicepay.NicepayTransaction;
import com.example.resay.global.infrastructure.nicepay.NicepayUnknownResultException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 충전 결제의 환불 (전액 취소).
 *
 *   PAID ──크레딧 회수──▶ CANCELLING ──취소 확인──▶ CANCELLED
 *                            └──취소 거절──▶ PAID (크레딧 되돌림)
 *
 * 지키는 것은 두 가지다.
 *  - 돈은 돌려받고 크레딧은 남는 일이 없게: 크레딧을 "먼저" 회수하고 커밋한 뒤에 나이스페이에 취소를 요청한다.
 *    충전한 크레딧을 이미 썼으면(잔액이 모자라면) 회수 단계에서 거절되고 취소를 요청하지 않는다.
 *  - 크레딧만 가져가고 돈은 돌려주지 않는 일이 없게: 취소되지 않은 것이 확인되면 회수한 크레딧을 되돌린다.
 *    취소 여부를 알 수 없으면 단정하지 않고 CANCELLING으로 두고, 정리 작업이 나이스페이에 조회해 맞춘다.
 *
 * 취소 하나를 여러 곳(취소 응답, 정리 작업, 웹훅)이 동시에 끝내려 할 수 있다. 취소 기록의 상태를 "지금 상태가 맞을 때만"
 * 바꾸므로 한 곳만 뒤처리를 한다. 외부 호출은 승인과 마찬가지로 DB 트랜잭션 밖에서 한다.
 *
 * 일부만 환불하는 것(부분 취소)은 하지 않는다. 충전한 크레딧을 일부 쓴 주문은 운영자가 따로 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCancelService {

    // 우리가 붙이는 실패 코드 (나이스페이의 결과 코드 4자와 겹치지 않는다): 취소 응답을 받지 못했고, 나중에 조회해 보니 취소되지 않았다
    static final String FAIL_NOT_CANCELLED = "NOT_CANCELLED";

    // 나이스페이에 남기는 취소 사유. 카드사 · 매출전표 쪽에 보일 수 있어 운영자가 적은 사유를 그대로 보내지 않는다
    private static final String PG_CANCEL_REASON = "크레딧 충전 환불";
    private static final String REASON_CANCELLED_AT_NICEPAY = "나이스페이에서 취소된 결제";

    private final PaymentRepository paymentRepository;
    private final PaymentCancelRepository paymentCancelRepository;
    private final NicepayClient nicepayClient;
    private final NicepayProperties nicepayProperties;
    private final CreditService creditService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 운영자가 요청한 환불. 충전한 크레딧이 그대로 남아 있는 주문만 전액 취소한다.
     *
     * @return 처리 뒤의 주문 · 취소 상태. 주문이 CANCELLING이면 취소 여부를 아직 확인하지 못한 것이고, 정리 작업이 몇 분 안에 확정한다
     * @throws GeneralException 결제 완료 상태가 아니거나(NOT_CANCELLABLE), 충전한 크레딧을 이미 썼거나(REFUND_EXCEEDS_BALANCE),
     *                          나이스페이가 취소를 거절했을 때(CANCEL_REJECTED. 회수한 크레딧은 되돌린 뒤다)
     */
    public PaymentCancelResponseDto cancel(String orderId, String reason, Long adminId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new GeneralException(PaymentErrorCode.PAYMENT_NOT_FOUND));
        // 키가 없으면 취소 요청을 보낼 수 없다. 크레딧만 회수한 채 멈추지 않도록 회수하기 전에 확인한다
        if (!nicepayProperties.isConfigured()) {
            throw new GeneralException(PaymentErrorCode.PAYMENT_UNAVAILABLE);
        }

        PaymentCancel cancel = startCancel(payment, reason, adminId);
        try {
            requestCancel(payment, cancel);
        } catch (RuntimeException exception) {
            // 크레딧은 이미 회수했다. 여기서 오류로 끝내면 운영자가 "아무 일도 없었다"고 보게 되므로 지금 상태를 그대로 돌려준다.
            // 주문은 CANCELLING으로 남아 정리 작업이 이어받는다
            log.error("충전 환불 처리 중 오류가 발생했습니다: orderId={}", payment.getOrderId(), exception);
        }
        PaymentCancelResponseDto result = PaymentCancelResponseDto.of(reload(payment), reload(cancel));
        if (result.cancelStatus() == PaymentCancelStatus.FAILED) {
            throw new GeneralException(PaymentErrorCode.CANCEL_REJECTED, result);
        }
        return result;
    }

    /**
     * 취소를 요청한 뒤 결과가 확정되지 않은 건을 나이스페이에 조회해서 맞춘다 (정리 작업용).
     * 취소 요청이 끝났을 만큼 시간이 지난 건에만 호출한다. 그 전에 "취소되지 않음"으로 끝내면 뒤늦게 처리된 취소와 어긋난다.
     *
     * @throws NicepayUnknownResultException 조회 응답을 받지 못했을 때. 그대로 두고 다음에 다시 시도한다
     */
    public void settleStuckCancel(PaymentCancel cancel) {
        Payment payment = paymentRepository.findById(cancel.getPaymentId())
                .orElseThrow(() -> new IllegalStateException("취소 기록의 주문을 찾을 수 없습니다."));
        NicepayTransaction found = nicepayClient.find(payment.getTid());
        settleByLookup(payment, cancel, found, FAIL_NOT_CANCELLED, "결제 취소가 처리되지 않았습니다.");
    }

    /**
     * 나이스페이가 "이 거래가 취소됐다"고 알려 왔을 때(웹훅) 호출한다. 알려 온 내용은 믿지 않고 조회한 결과로만 판단한다.
     *
     *  - 우리가 요청한 취소가 확인을 기다리는 중이면 그 취소를 완료한다
     *  - 우리가 요청한 적 없는 취소면(나이스페이 관리자 화면에서 취소한 경우) 주문을 환불로 바꾸고 남아 있는 크레딧을 회수한다
     *
     * 조회 결과가 취소가 아니면 아무것도 하지 않는다. 취소 요청이 아직 진행 중일 수 있어서, 여기서는 "취소되지 않음"으로 끝내지 않는다.
     *
     * @throws NicepayUnknownResultException 조회 응답을 받지 못했을 때
     */
    public void syncCancelledAtNicepay(Payment payment) {
        NicepayTransaction found = nicepayClient.find(payment.getTid());
        if (!isCancelled(payment, found)) {
            log.warn("취소 통보를 받았지만 조회 결과는 전액 취소가 아닙니다: orderId={}, resultCode={}, status={}",
                    payment.getOrderId(), found.resultCode(), found.status());
            return;
        }

        PaymentStatus status = currentStatus(payment);
        if (status == PaymentStatus.CANCELLING) {
            paymentCancelRepository
                    .findFirstByPaymentIdAndStatusOrderByIdDesc(payment.getId(), PaymentCancelStatus.REQUESTED)
                    .ifPresent(cancel -> complete(payment, cancel, found.cancelledAt()));
        } else if (status == PaymentStatus.PAID) {
            recordCancelledAtNicepay(payment, found.cancelledAt());
        }
    }

    /**
     * 환불을 시작한다: 주문을 CANCELLING으로 바꾸고, 취소 기록을 남기고, 크레딧을 회수한다 (한 트랜잭션, 바로 커밋된다).
     * 결제 완료 상태가 아니거나 잔액이 모자라면 예외와 함께 전부 되돌아간다.
     */
    private PaymentCancel startCancel(Payment payment, String reason, Long adminId) {
        LocalDateTime now = LocalDateTime.now();
        return transactionTemplate.execute(status -> {
            // 같은 주문의 환불 요청이 동시에 와도 한 요청만 1을 받는다
            if (paymentRepository.markCancelling(payment.getId(), now) != 1) {
                throw new GeneralException(
                        PaymentErrorCode.NOT_CANCELLABLE, Map.of("status", currentStatus(payment)));
            }
            PaymentCancel cancel = paymentCancelRepository.save(PaymentCancel.request(
                    payment, newCancelOrderId(), truncate(reason, PaymentCancel.REASON_MAX_LENGTH), adminId));
            creditService.collectChargeRefund(
                    payment.getUserId(), payment.getId(), cancel.getId(), cancel.getCreditAmount());
            return cancel;
        });
    }

    private void requestCancel(Payment payment, PaymentCancel cancel) {
        NicepayTransaction response;
        try {
            response = nicepayClient.cancel(payment.getTid(), cancel.getCancelOrderId(), PG_CANCEL_REASON);
        } catch (NicepayUnknownResultException exception) {
            // 취소됐을 수도 있다. 크레딧은 회수한 채로 두고 정리 작업이 조회해서 맞춘다
            log.warn("취소 결과를 확인하지 못했습니다. 정리 작업이 다시 확인합니다: orderId={}", payment.getOrderId());
            return;
        }

        if (isCancelled(payment, response)) {
            complete(payment, cancel, response.cancelledAt());
        } else if (response.isSuccess()) {
            // 요청은 성공인데 전액 취소로 확인되지 않는 응답. 나오지 않아야 하므로 단정하지 않고 정리 작업에 넘긴다
            log.warn("취소 응답이 전액 취소로 확인되지 않습니다. 정리 작업이 다시 확인합니다: orderId={}, status={}",
                    payment.getOrderId(), response.status());
        } else {
            settleRejectedCancel(payment, cancel, response);
        }
    }

    // 취소가 거절됐다는 답이다. "이미 취소된 거래"라서 거절된 것일 수 있으므로, 크레딧을 되돌리기 전에 거래를 조회해 본다
    private void settleRejectedCancel(Payment payment, PaymentCancel cancel, NicepayTransaction rejection) {
        NicepayTransaction found;
        try {
            found = nicepayClient.find(payment.getTid());
        } catch (NicepayUnknownResultException exception) {
            log.warn("취소 거절 뒤 조회 응답을 받지 못했습니다. 정리 작업이 다시 확인합니다: orderId={}", payment.getOrderId());
            return;
        }
        settleByLookup(payment, cancel, found, rejection.resultCode(), rejection.resultMsg());
    }

    /**
     * 조회 결과로 취소를 끝낸다. 취소됐으면 완료, 결제가 그대로면 실패로 끝내고 크레딧을 되돌린다.
     *
     * @param failCode    취소되지 않은 것으로 끝낼 때 남길 코드
     * @param failMessage 취소되지 않은 것으로 끝낼 때 남길 문구
     */
    private void settleByLookup(
            Payment payment, PaymentCancel cancel, NicepayTransaction found, String failCode, String failMessage
    ) {
        if (found.signature() == NicepayTransaction.Signature.INVALID) {
            log.error("나이스페이 응답의 서명이 맞지 않아 취소 여부를 확정하지 않았습니다: orderId={}", payment.getOrderId());
        } else if (isCancelled(payment, found)) {
            complete(payment, cancel, found.cancelledAt());
        } else if (found.isPaid() || found.isNotFound()) {
            // 결제가 그대로 살아 있거나, 나이스페이에 그런 거래가 없다 (다른 상점 키로 결제된 주문 등). 어느 쪽이든 환불되지 않았다
            reject(payment, cancel, failCode, failMessage);
        } else {
            // 조회가 거절됐거나(키 오류 등) 전액 취소도 결제 완료도 아닌 상태(일부만 취소됨 등)다. 사람이 봐야 한다
            log.warn("취소 중인 주문의 취소 여부를 확인하지 못했습니다: orderId={}, resultCode={}, status={}",
                    payment.getOrderId(), found.resultCode(), found.status());
        }
    }

    private void complete(Payment payment, PaymentCancel cancel, LocalDateTime nicepayCancelledAt) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cancelledAt = nicepayCancelledAt != null ? nicepayCancelledAt : now;
        Boolean completed = transactionTemplate.execute(status -> {
            // 다른 곳이 먼저 끝냈으면 0건이 바뀐다
            if (paymentCancelRepository.markDone(cancel.getId(), cancelledAt, now) != 1) {
                return false;
            }
            requireSingleRow(paymentRepository.markCancelled(
                    payment.getId(), PaymentStatus.CANCELLING, cancelledAt, now));
            return true;
        });
        if (Boolean.TRUE.equals(completed)) {
            log.info("충전 환불 완료: orderId={}, amount={}", payment.getOrderId(), cancel.getAmount());
        }
    }

    // 취소되지 않았다. 주문을 결제 완료로 되돌리고 회수한 크레딧을 돌려준다 (한 트랜잭션)
    private void reject(Payment payment, PaymentCancel cancel, String failCode, String failMessage) {
        LocalDateTime now = LocalDateTime.now();
        String code = truncate(failCode, PaymentCancel.FAIL_CODE_MAX_LENGTH);
        Boolean rejected = transactionTemplate.execute(status -> {
            // 다른 곳이 먼저 끝냈으면 0건이 바뀌고 크레딧도 다시 되돌리지 않는다
            if (paymentCancelRepository.markFailed(
                    cancel.getId(), code, truncate(failMessage, PaymentCancel.FAIL_MESSAGE_MAX_LENGTH), now) != 1) {
                return false;
            }
            requireSingleRow(paymentRepository.revertCancelling(payment.getId(), now));
            creditService.undoChargeRefund(
                    payment.getUserId(), payment.getId(), cancel.getId(), cancel.getCreditAmount());
            return true;
        });
        if (Boolean.TRUE.equals(rejected)) {
            log.warn("충전 환불 실패. 회수한 크레딧을 되돌렸습니다: orderId={}, failCode={}", payment.getOrderId(), code);
        }
    }

    /**
     * 우리가 요청하지 않았는데 나이스페이에서 취소돼 있는 결제를 반영한다. 돈은 이미 돌아갔으므로 크레딧을 회수한다.
     * 사용자가 그 크레딧을 이미 썼으면 남아 있는 만큼만 회수하고, 모자란 만큼은 오류 로그로 남긴다 (사람이 처리한다).
     */
    private void recordCancelledAtNicepay(Payment payment, LocalDateTime nicepayCancelledAt) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cancelledAt = nicepayCancelledAt != null ? nicepayCancelledAt : now;
        Integer collected = transactionTemplate.execute(status -> {
            // 같은 통보가 겹쳐 오거나 그사이 운영자가 환불을 시작했으면 0건이 바뀐다
            if (paymentRepository.markCancelled(payment.getId(), PaymentStatus.PAID, cancelledAt, now) != 1) {
                return null;
            }
            PaymentCancel cancel = paymentCancelRepository.save(
                    PaymentCancel.alreadyCancelled(payment, REASON_CANCELLED_AT_NICEPAY, cancelledAt));
            int credits = creditService.collectAvailableChargeRefund(
                    payment.getUserId(), payment.getId(), cancel.getId(), payment.getCreditAmount());
            cancel.recordCollectedCredits(credits);
            return credits;
        });
        if (collected == null) {
            return;
        }
        if (collected < payment.getCreditAmount()) {
            log.error("나이스페이에서 취소된 결제의 크레딧을 전부 회수하지 못했습니다 (이미 사용함): orderId={}, 회수={}, 부족={}",
                    payment.getOrderId(), collected, payment.getCreditAmount() - collected);
        } else {
            log.warn("나이스페이에서 취소된 결제를 환불로 반영하고 크레딧을 회수했습니다: orderId={}, amount={}",
                    payment.getOrderId(), payment.getAmount());
        }
    }

    // 이 주문의 거래가 전액 취소됐다는 답인지. 서명이 실려 왔는데 맞지 않으면 믿지 않는다
    private static boolean isCancelled(Payment payment, NicepayTransaction transaction) {
        return transaction.isCancelled()
                && payment.getTid().equals(transaction.tid())
                && transaction.signature() != NicepayTransaction.Signature.INVALID;
    }

    private PaymentStatus currentStatus(Payment payment) {
        return reload(payment).getStatus();
    }

    private Payment reload(Payment payment) {
        return paymentRepository.findById(payment.getId()).orElse(payment);
    }

    private PaymentCancel reload(PaymentCancel cancel) {
        return paymentCancelRepository.findById(cancel.getId()).orElse(cancel);
    }

    // 주문 상태와 취소 기록은 항상 같이 바뀐다. 한쪽만 바뀌는 상황이면 트랜잭션을 되돌린다
    private static void requireSingleRow(int updated) {
        if (updated != 1) {
            throw new IllegalStateException("취소 기록과 주문 상태가 맞지 않습니다.");
        }
    }

    // 취소 요청마다 새로 만드는 번호 (나이스페이 제한 64자 안)
    private static String newCancelOrderId() {
        return "RC" + UUID.randomUUID().toString().replace("-", "");
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
