package com.example.resay.domain.payment.dto;

import com.example.resay.domain.payment.entity.Payment;
import com.example.resay.domain.payment.entity.PaymentCancel;
import com.example.resay.domain.payment.entity.PaymentCancelStatus;
import com.example.resay.domain.payment.entity.PaymentStatus;
import java.time.LocalDateTime;

public record PaymentCancelResponseDto(
        String orderId,
        // CANCELLED: 환불 완료. CANCELLING: 취소 여부 확인 중(몇 분 안에 확정된다). PAID: 취소가 거절돼 그대로다
        PaymentStatus status,
        // DONE: 취소됨. REQUESTED: 확인 중. FAILED: 취소되지 않았고 회수한 크레딧을 되돌렸다
        PaymentCancelStatus cancelStatus,
        // 환불 금액(원)
        int amount,
        // 회수한 크레딧 (cancelStatus가 FAILED면 되돌린 상태다)
        int credits,
        LocalDateTime cancelledAt,
        // 취소가 거절된 이유 (나이스페이가 보낸 코드 · 문구). FAILED일 때만 있다
        String failCode,
        String failMessage
) {

    public static PaymentCancelResponseDto of(Payment payment, PaymentCancel cancel) {
        return new PaymentCancelResponseDto(
                payment.getOrderId(),
                payment.getStatus(),
                cancel.getStatus(),
                cancel.getAmount(),
                cancel.getCreditAmount(),
                cancel.getCancelledAt(),
                cancel.getFailCode(),
                cancel.getFailMessage()
        );
    }
}
