package com.example.resay.domain.payment.service;

import com.example.resay.domain.payment.code.PaymentErrorCode;
import com.example.resay.domain.payment.dto.NicepayWebhookRequestDto;
import com.example.resay.domain.payment.entity.Payment;
import com.example.resay.domain.payment.entity.PaymentStatus;
import com.example.resay.domain.payment.repository.PaymentRepository;
import com.example.resay.global.exception.GeneralException;
import com.example.resay.global.infrastructure.nicepay.NicepaySignature;
import com.example.resay.global.infrastructure.nicepay.NicepayTransaction;
import com.example.resay.global.infrastructure.nicepay.NicepayUnknownResultException;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * 나이스페이가 결제 · 취소가 일어났을 때 서버로 보내는 통보(웹훅)의 처리.
 *
 * 통보는 "이 거래를 확인해 보라"는 신호로만 쓴다. 로그인 없이 누구나 보낼 수 있는 주소이고, 서명은 상태 값을 덮지 않아서
 * 내용을 그대로 반영하지 않는다. 서명이 맞는 통보일 때 나이스페이에 그 거래를 조회하고, 조회한 결과로만 주문을 바꾼다.
 *
 * 반영하는 것은 "돈이 돌아간(돌아가야 하는)" 경우뿐이다.
 *  - 취소 통보 + 우리가 요청한 취소가 확인을 기다리는 중: 정리 작업을 기다리지 않고 바로 환불로 확정한다
 *  - 취소 통보 + 결제 완료 주문: 나이스페이 관리자 화면에서 직접 취소한 결제다. 환불로 바꾸고 크레딧을 회수한다
 *    (이 경로가 아니면 알 방법이 없다)
 *  - 결제 통보 + 결제되지 않은 것으로 끝낸 주문: 뒤늦게 결제가 잡힌 것이다. 크레딧을 줄 수 없으므로 그 결제를 취소한다
 *
 * 결제 통보로 주문을 결제 완료로 바꾸지는 않는다. 승인 요청이 진행 중일 때 통보를 보고 먼저 확정하면, 응답을 받지 못한
 * 승인 요청이 망취소를 보내 돈은 취소되고 크레딧만 남을 수 있다. 결제 확정은 승인 흐름과 정리 작업만 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentWebhookService {

    private static final Pattern TID_PATTERN = Pattern.compile("[A-Za-z0-9]{1,30}");
    // 로그에 남겨도 되는 상태 값의 형식 (paid, cancelled, partialCancelled 등)
    private static final Pattern STATUS_PATTERN = Pattern.compile("[A-Za-z]{1,20}");
    private static final String STATUS_PAID = "paid";
    private static final Set<String> CANCEL_STATUSES = Set.of("cancelled", "partialCancelled");

    private final PaymentRepository paymentRepository;
    private final PaymentApprovalService paymentApprovalService;
    private final PaymentCancelService paymentCancelService;
    private final NicepaySignature nicepaySignature;
    private final ObjectMapper objectMapper;

    /**
     * @return 통보를 처리했거나 처리할 것이 없으면 true. 처리하지 못했으면 false (나이스페이가 실패로 기록하므로 관리자 화면에서 다시 보낼 수 있다)
     * @throws GeneralException 본문을 읽을 수 없을 때
     */
    public boolean handle(byte[] body) {
        NicepayWebhookRequestDto event = parse(body);
        if (event.tid() == null || !TID_PATTERN.matcher(event.tid()).matches()) {
            return true;
        }
        // 우리 주문의 거래가 아니다 (나이스페이 관리자 화면의 테스트 호출 등)
        Optional<Payment> found = paymentRepository.findByTid(event.tid());
        if (found.isEmpty()) {
            return true;
        }
        Payment payment = found.get();

        NicepayTransaction.Signature signature = nicepaySignature.checkTransaction(
                event.tid(), event.amount() == null ? 0 : event.amount(), event.ediDate(), event.signature());
        if (event.amount() == null || signature != NicepayTransaction.Signature.VALID) {
            log.warn("서명을 확인할 수 없는 나이스페이 통보를 무시했습니다: orderId={}, 서명={}",
                    payment.getOrderId(), signature);
            return true;
        }

        String notifiedStatus = loggable(event.status());
        log.info("나이스페이 통보: orderId={}, 통보 상태={}, 주문 상태={}",
                payment.getOrderId(), notifiedStatus, payment.getStatus());
        try {
            reflect(payment, notifiedStatus);
            return true;
        } catch (NicepayUnknownResultException exception) {
            log.warn("나이스페이 통보를 받았지만 거래를 조회하지 못했습니다: orderId={}", payment.getOrderId());
            return false;
        }
    }

    private void reflect(Payment payment, String notifiedStatus) {
        PaymentStatus status = payment.getStatus();
        if (CANCEL_STATUSES.contains(notifiedStatus)) {
            // 결제 완료 상태(나이스페이 쪽에서 취소된 경우)이거나, 우리가 요청한 취소가 확인을 기다리는 중일 때만 조회한다
            if (status == PaymentStatus.PAID || status == PaymentStatus.CANCELLING) {
                paymentCancelService.syncCancelledAtNicepay(payment);
            }
        } else if (STATUS_PAID.equals(notifiedStatus) && status == PaymentStatus.FAILED) {
            // FAILED는 승인 요청이 모두 끝난 뒤의 상태라, 진행 중인 승인 요청과 겹치지 않는다
            paymentApprovalService.refundPaymentOfFailedOrder(payment);
        }
    }

    private NicepayWebhookRequestDto parse(byte[] body) {
        if (body == null || body.length == 0) {
            throw new GeneralException(PaymentErrorCode.INVALID_WEBHOOK);
        }
        NicepayWebhookRequestDto event;
        try {
            event = objectMapper.readValue(body, NicepayWebhookRequestDto.class);
        } catch (RuntimeException exception) {
            throw new GeneralException(PaymentErrorCode.INVALID_WEBHOOK);
        }
        if (event == null) {
            throw new GeneralException(PaymentErrorCode.INVALID_WEBHOOK);
        }
        return event;
    }

    private static String loggable(String status) {
        return status != null && STATUS_PATTERN.matcher(status).matches() ? status : "unknown";
    }
}
