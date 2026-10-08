package com.example.resay.domain.payment.service;

import com.example.resay.domain.credit.service.CreditService;
import com.example.resay.domain.payment.code.PaymentErrorCode;
import com.example.resay.domain.payment.config.PaymentProperties;
import com.example.resay.domain.payment.dto.NicepayReturnRequestDto;
import com.example.resay.domain.payment.entity.Payment;
import com.example.resay.domain.payment.entity.PaymentStatus;
import com.example.resay.domain.payment.repository.PaymentRepository;
import com.example.resay.global.exception.GeneralException;
import com.example.resay.global.infrastructure.nicepay.NicepayClient;
import com.example.resay.global.infrastructure.nicepay.NicepayProperties;
import com.example.resay.global.infrastructure.nicepay.NicepaySignature;
import com.example.resay.global.infrastructure.nicepay.NicepayTransaction;
import com.example.resay.global.infrastructure.nicepay.NicepayUnknownResultException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 충전 결제의 승인과 확정.
 *
 * 결제창에서 카드 인증이 끝나면 인증 결과가 이 서버로 오고, 우리가 승인 API를 불러야 실제 결제가 일어난다.
 *
 *   READY ──인증 결과 확인──▶ APPROVING ──승인 성공──▶ PAID (+크레딧)
 *     │                          └──승인 거절 · 망취소──▶ FAILED
 *     └──인증 실패 · 값 불일치──▶ FAILED
 *
 * 지키는 것은 두 가지다.
 *  - 돈이 나갔는데 크레딧이 없는 일이 없게: 승인을 요청하기 "전에" APPROVING과 tid를 저장하고 커밋한다.
 *    승인 뒤에 서버가 죽거나 답을 못 받아도 주문이 APPROVING으로 남아, 정리 작업이 나이스페이에 조회해 맞춘다.
 *    결제됐다는 답을 받고도 지급할 수 없는 경우(이 주문의 결제가 아님)에는 그 결제를 취소한다.
 *  - 크레딧이 두 번 지급되는 일이 없게: 상태는 "지금 상태가 맞을 때만" 바뀌므로 한 요청만 다음 단계로 가고,
 *    PAID 전환과 크레딧 지급은 한 트랜잭션이다.
 *
 * 결제 여부를 알 수 없을 때는 실패로 단정하지 않고 APPROVING으로 둔다. FAILED는 결제되지 않았음이 확인된 주문이다.
 *
 * 외부 호출(승인 · 망취소 · 조회)은 DB 트랜잭션 밖에서 한다. 최대 30초가 걸려서 그동안 DB 연결과 잠금을 잡고 있으면 안 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentApprovalService {

    static final String SUCCESS_PATH = "/payments/success";
    static final String FAIL_PATH = "/payments/fail";

    // 우리가 붙이는 실패 코드. 나이스페이의 결과 코드(4자)와 겹치지 않는다
    static final String FAIL_AUTH_FAILED = "AUTH_FAILED";
    static final String FAIL_INVALID_AUTH_RESULT = "INVALID_AUTH_RESULT";
    static final String FAIL_NET_CANCELLED = "NET_CANCELLED";
    static final String FAIL_NOT_PAID = "NOT_PAID";
    // 나이스페이는 결제됐다고 하는데 이 주문의 결제가 아닌 경우. 망취소가 확인되지 않았으면 사람이 취소해야 한다
    static final String FAIL_RESULT_MISMATCH = "RESULT_MISMATCH";

    private static final String AUTH_SUCCESS_CODE = "0000";
    // 나이스페이 결과 코드의 형식. 로그인 없이 받는 값이라 이 형식일 때만 그대로 저장 · 기록한다
    private static final Pattern RESULT_CODE_PATTERN = Pattern.compile("[A-Za-z0-9]{4}");
    // tid는 승인 API 주소에 들어간다. 나이스페이가 만드는 형식(영문 · 숫자 30자 이내)만 받는다
    private static final Pattern TID_PATTERN = Pattern.compile("[A-Za-z0-9]{1,30}");

    private final PaymentRepository paymentRepository;
    private final NicepayClient nicepayClient;
    private final NicepaySignature nicepaySignature;
    private final NicepayProperties nicepayProperties;
    private final PaymentProperties paymentProperties;
    private final CreditService creditService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 결제창이 보낸 인증 결과를 처리한다.
     *
     * @return 사용자를 돌려보낼 프론트 화면 주소. 주소가 성공 화면이라고 결제가 된 것은 아니다.
     *         프론트는 주문번호로 결과를 다시 조회해 표시한다
     */
    public URI handleAuthResult(NicepayReturnRequestDto authResult) {
        Payment payment = paymentRepository.findByOrderId(authResult.orderId() == null ? "" : authResult.orderId())
                .orElseThrow(() -> new GeneralException(PaymentErrorCode.INVALID_AUTH_RESULT));
        try {
            process(payment, authResult);
        } catch (RuntimeException exception) {
            // 처리 중 오류가 나도 사용자를 오류 화면에 남겨 두지 않는다. 주문 상태가 곧 결과다.
            // 승인을 요청한 뒤라면 주문은 APPROVING으로 남아 정리 작업이 이어받는다
            log.error("결제 인증 결과 처리 중 오류가 발생했습니다: orderId={}", payment.getOrderId(), exception);
        }
        return resultPage(payment, currentStatus(payment));
    }

    /**
     * 승인을 요청한 뒤 결과가 확정되지 않은 주문을 나이스페이에 조회해서 맞춘다 (정리 작업용).
     *
     * @throws NicepayUnknownResultException 조회 응답을 받지 못했을 때. 주문은 그대로 두고 다음에 다시 시도한다
     */
    public void settleStuckApproval(Payment payment) {
        NicepayTransaction found = nicepayClient.find(payment.getTid());
        if (found.isPaid()) {
            confirmPaid(payment, payment.getTid(), found);
        } else if (found.isNotPaid()) {
            // 인증만 하고 승인되지 않았거나, 취소됐거나, 그런 거래가 없다
            fail(payment, PaymentStatus.APPROVING, FAIL_NOT_PAID, "결제가 완료되지 않았습니다.");
        } else {
            // 조회가 거절됐거나(키 오류 등) 처음 보는 상태다. 결제 여부를 알 수 없으므로 그대로 둔다
            log.warn("승인 중인 주문의 결제 여부를 확인하지 못했습니다: orderId={}, resultCode={}, status={}",
                    payment.getOrderId(), found.resultCode(), found.status());
        }
    }

    private void process(Payment payment, NicepayReturnRequestDto authResult) {
        // 인증 결과를 이미 받은 주문이다 (같은 결과가 연달아 전송되거나, 뒤로 가기로 다시 전송된 경우)
        if (payment.getStatus() != PaymentStatus.READY) {
            return;
        }
        if (!AUTH_SUCCESS_CODE.equals(authResult.authResultCode())) {
            // 카드 인증에 실패했거나 사용자가 취소했다 (모바일). 승인을 요청하지 않으므로 결제는 일어나지 않는다
            fail(payment, PaymentStatus.READY, pgCodeOr(authResult.authResultCode(), FAIL_AUTH_FAILED),
                    authResult.authResultMsg());
            return;
        }
        if (!matchesOrder(payment, authResult)) {
            log.warn("결제 인증 결과가 주문과 맞지 않습니다: orderId={}", payment.getOrderId());
            fail(payment, PaymentStatus.READY, FAIL_INVALID_AUTH_RESULT, "결제 정보가 주문과 일치하지 않습니다.");
            return;
        }
        if (!startApproval(payment, authResult.tid(), authResult.authToken())) {
            return;
        }

        NicepayTransaction approval;
        try {
            approval = approve(payment, authResult.tid());
        } catch (NicepayUnknownResultException exception) {
            cancelUnknownApproval(payment);
            return;
        }

        if (approval.isPaid()) {
            confirmPaid(payment, authResult.tid(), approval);
        } else if (approval.isSuccess()) {
            // 요청은 성공인데 결제 완료가 아닌 응답. 카드 결제에서는 나오지 않아야 하므로 단정하지 않고 정리 작업에 넘긴다
            log.warn("승인 응답의 상태가 결제 완료가 아닙니다: orderId={}, status={}",
                    payment.getOrderId(), approval.status());
        } else {
            settleRejectedApproval(payment, authResult.tid(), approval);
        }
    }

    /**
     * 승인을 요청한다. "그런 인증 내역이 없다"는 답이면 잠깐 기다렸다 다시 요청한다.
     *
     * 인증 결과를 받자마자 승인을 요청하면 나이스페이가 인증 내역을 아직 찾지 못할 때가 있다
     * (샌드박스에서 확인: 인증 직후에는 404 U121, 같은 요청을 얼마 뒤에 보내니 승인됐다).
     * 여기까지 온 인증 결과는 서명을 확인한 것이므로 내역이 곧 보일 것으로 보고 기다린다.
     * 이 답은 아무것도 승인되지 않았다는 뜻이라 다시 요청해도 두 번 결제되지 않는다.
     */
    private NicepayTransaction approve(Payment payment, String tid) {
        long startedAt = System.nanoTime();
        NicepayTransaction approval = nicepayClient.approve(tid, payment.getAmount());
        int retries = 0;
        for (Duration delay : paymentProperties.approvalRetryDelays()) {
            if (!approval.isMissing() || !sleep(delay)) {
                break;
            }
            retries++;
            approval = nicepayClient.approve(tid, payment.getAmount());
        }
        if (retries > 0) {
            log.info("승인 요청을 {}번 다시 보냈습니다: orderId={}, resultCode={}, 걸린 시간={}ms",
                    retries, payment.getOrderId(), approval.resultCode(),
                    Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        }
        return approval;
    }

    // 금액은 주문에 저장된 값으로, 서명은 우리 시크릿 키로 다시 계산해 비교한다.
    // 서명에는 tid와 주문번호가 들어가지 않는다. 그 둘은 승인 응답을 받은 뒤 confirmPaid에서 주문과 대조한다
    private boolean matchesOrder(Payment payment, NicepayReturnRequestDto authResult) {
        return nicepayProperties.clientKey().equals(authResult.clientId())
                && String.valueOf(payment.getAmount()).equals(authResult.amount())
                && authResult.tid() != null
                && TID_PATTERN.matcher(authResult.tid()).matches()
                && nicepaySignature.matchesAuthResult(
                        authResult.authToken(), authResult.clientId(), payment.getAmount(), authResult.signature());
    }

    /**
     * 승인을 요청하기 전에 주문을 APPROVING으로 바꾸고 tid를 남긴다 (바로 커밋된다).
     *
     * 인증 결과의 서명은 주문번호와 tid를 덮지 않아서, 서명이 맞는 인증 결과 하나를 다른 주문 · 다른 tid에 붙여 다시 보낼 수 있다.
     * 그래서 인증 결과(토큰)와 tid는 각각 한 주문에만 쓸 수 있게 DB 유니크로 막는다.
     *
     * @return 이 요청이 승인을 진행해도 되면 true. 같은 인증 결과를 다른 요청이 먼저 처리하고 있으면 false
     */
    private boolean startApproval(Payment payment, String tid, String authToken) {
        try {
            return paymentRepository.markApproving(
                    payment.getId(), tid, nicepaySignature.fingerprint(authToken), LocalDateTime.now()) == 1;
        } catch (DataIntegrityViolationException exception) {
            log.warn("이미 사용된 인증 결과입니다: orderId={}", payment.getOrderId());
            fail(payment, PaymentStatus.READY, FAIL_INVALID_AUTH_RESULT, "결제 정보가 주문과 일치하지 않습니다.");
            return false;
        }
    }

    // 승인 요청의 답을 받지 못했다. 결제됐을 수도 있으므로 나이스페이 안내대로 망취소를 요청한다.
    // 망취소는 주문번호로 거래를 찾는다 (결제 시도마다 주문번호를 새로 만들기 때문에 이 주문의 거래만 해당한다)
    private void cancelUnknownApproval(Payment payment) {
        boolean cancelled;
        try {
            cancelled = nicepayClient.netCancel(payment.getOrderId()).isCancelled();
        } catch (RuntimeException exception) {
            cancelled = false;
        }
        if (cancelled) {
            fail(payment, PaymentStatus.APPROVING, FAIL_NET_CANCELLED,
                    "결제 결과를 확인하지 못해 취소했습니다. 다시 시도해 주세요.");
            return;
        }
        // 취소가 확인되지 않았다 (승인이 아직 처리 중이었거나, 애초에 승인되지 않았거나, 통신이 계속 안 되는 경우).
        // 실패로 단정하지 않고 APPROVING으로 남긴다. 정리 작업이 조회해서 결제됐으면 크레딧을 지급한다
        log.warn("승인 결과를 확인하지 못했습니다. 정리 작업이 다시 확인합니다: orderId={}", payment.getOrderId());
    }

    // 승인이 거절됐다는 답이다. 드물게 "오류로 답했지만 카드 승인은 난" 경우가 있을 수 있어, 실패로 끝내기 전에 거래를 조회해 본다
    private void settleRejectedApproval(Payment payment, String tid, NicepayTransaction rejection) {
        NicepayTransaction found;
        try {
            found = nicepayClient.find(tid);
        } catch (NicepayUnknownResultException exception) {
            log.warn("승인 거절 뒤 조회 응답을 받지 못했습니다. 정리 작업이 다시 확인합니다: orderId={}", payment.getOrderId());
            return;
        }
        if (found.isPaid()) {
            log.warn("승인은 거절로 답했지만 조회 결과는 결제 완료입니다: orderId={}, resultCode={}",
                    payment.getOrderId(), rejection.resultCode());
            confirmPaid(payment, tid, found);
            return;
        }
        // 한도 초과 같은 카드사 거절, 만료된 인증 등
        fail(payment, PaymentStatus.APPROVING, rejection.resultCode(), rejection.resultMsg());
    }

    private void confirmPaid(Payment payment, String tid, NicepayTransaction paid) {
        if (!payment.getOrderId().equals(paid.orderId())
                || payment.getAmount() != paid.amount()
                || !tid.equals(paid.tid())) {
            // 결제는 됐는데 이 주문의 결제가 아니다 (다른 주문의 인증 결과를 끼워 넣은 경우). 크레딧을 지급하지 않고 그 결제를 취소한다
            boolean cancelled = cancelCapturedPayment(tid, "주문 정보 불일치");
            log.error("나이스페이 결제 결과가 주문과 맞지 않습니다: orderId={}, tid={}, 취소={}",
                    payment.getOrderId(), tid, cancelled ? "완료" : "미확인 (나이스페이 관리자에서 취소 필요)");
            fail(payment, PaymentStatus.APPROVING, FAIL_RESULT_MISMATCH, cancelled
                    ? "결제 정보가 주문과 일치하지 않아 결제를 취소했습니다."
                    : "결제 정보가 주문과 일치하지 않습니다.");
            return;
        }
        if (paid.signature() == NicepayTransaction.Signature.INVALID) {
            // 값은 주문과 맞는데 서명이 다르다. 지급도 실패 처리도 하지 않고 APPROVING으로 둔다 (정리 작업이 조회로 다시 확인한다)
            log.error("나이스페이 응답의 서명이 맞지 않아 결제를 확정하지 않았습니다: orderId={}, tid={}",
                    payment.getOrderId(), tid);
            return;
        }
        if (paid.signature() == NicepayTransaction.Signature.MISSING) {
            // 서명은 응답의 필수 항목이 아니다. 우리가 직접 호출해 받은 응답이고 주문번호 · tid · 금액이 맞으므로 진행한다
            log.warn("나이스페이 응답에 서명이 없습니다: orderId={}", payment.getOrderId());
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime paidAt = paid.paidAt() != null ? paid.paidAt() : now;
        Boolean confirmed = transactionTemplate.execute(status -> {
            // 다른 요청(같은 인증 결과의 중복 전송, 정리 작업)이 먼저 확정했으면 0건이 바뀌고 크레딧도 다시 지급하지 않는다
            if (paymentRepository.markPaid(payment.getId(), paidAt, fitOrNull(paid.receiptUrl(), 200), now) != 1) {
                return false;
            }
            creditService.charge(payment.getUserId(), payment.getId(), payment.getCreditAmount());
            return true;
        });
        if (Boolean.TRUE.equals(confirmed)) {
            log.info("충전 완료: orderId={}, amount={}", payment.getOrderId(), payment.getAmount());
        } else if (currentStatus(payment) != PaymentStatus.PAID) {
            // 결제됐다는 답이 왔는데 주문은 이미 결제되지 않은 것으로 끝나 있다. 크레딧을 줄 수 없으므로 결제를 취소한다
            boolean cancelled = cancelCapturedPayment(tid, "종료된 주문");
            log.error("이미 끝난 주문에 결제 완료 응답이 왔습니다: orderId={}, tid={}, 취소={}",
                    payment.getOrderId(), tid, cancelled ? "완료" : "미확인 (나이스페이 관리자에서 취소 필요)");
        }
    }

    /**
     * 나이스페이에 결제가 잡혔지만 크레딧을 지급하지 않는 경우, 방금 승인(또는 조회)한 그 거래를 취소한다.
     * 주문번호로 찾는 망취소가 아니라 tid를 지정하는 취소를 쓴다. 이 경우에는 응답의 주문번호를 믿을 수 없어서,
     * 주문번호로 취소하면 엉뚱한 거래(이미 크레딧을 지급한 결제)를 취소할 수 있다.
     *
     * @return 취소가 확인됐으면 true. 확인되지 않았으면(응답 없음, 취소 거절 등) false
     */
    private boolean cancelCapturedPayment(String tid, String reason) {
        try {
            String cancelOrderId = "RC" + UUID.randomUUID().toString().replace("-", "");
            NicepayTransaction cancelled = nicepayClient.cancel(tid, cancelOrderId, reason);
            return cancelled != null && cancelled.isCancelled();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void fail(Payment payment, PaymentStatus expected, String failCode, String failMessage) {
        String code = truncate(failCode, Payment.FAIL_CODE_MAX_LENGTH);
        int updated = paymentRepository.markFailed(
                payment.getId(), expected, code, truncate(failMessage, Payment.FAIL_MESSAGE_MAX_LENGTH),
                LocalDateTime.now());
        if (updated == 1) {
            log.info("충전 실패: orderId={}, failCode={}", payment.getOrderId(), code);
        }
    }

    private PaymentStatus currentStatus(Payment payment) {
        return paymentRepository.findById(payment.getId())
                .map(Payment::getStatus)
                .orElse(payment.getStatus());
    }

    private URI resultPage(Payment payment, PaymentStatus status) {
        // APPROVING은 결과를 확인하는 중이다. 성공 화면으로 보내고, 화면이 상태를 다시 조회해 확정될 때까지 기다린다
        boolean successPage = status == PaymentStatus.PAID || status == PaymentStatus.APPROVING;
        return UriComponentsBuilder.fromUriString(payment.getResultOrigin())
                .path(successPage ? SUCCESS_PATH : FAIL_PATH)
                .queryParam("orderId", payment.getOrderId())
                .build()
                .toUri();
    }

    // 로그인 없이 받은 코드는 나이스페이 결과 코드 형식일 때만 쓴다 (로그에 엉뚱한 줄을 만들거나 우리 코드를 흉내 내지 못하게)
    private static String pgCodeOr(String code, String fallback) {
        return code != null && RESULT_CODE_PATTERN.matcher(code).matches() ? code : fallback;
    }

    // @return 다 기다렸으면 true. 기다리는 중에 중단 요청(서버 종료 등)이 오면 false
    private static boolean sleep(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    // 잘린 주소는 쓸모가 없으므로 길이를 넘으면 저장하지 않는다
    private static String fitOrNull(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : null;
    }
}
