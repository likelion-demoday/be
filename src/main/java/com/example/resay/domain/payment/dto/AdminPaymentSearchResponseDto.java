package com.example.resay.domain.payment.dto;

import com.example.resay.domain.payment.entity.Payment;
import com.example.resay.domain.payment.entity.PaymentStatus;
import java.time.LocalDateTime;
import java.util.List;

public record AdminPaymentSearchResponseDto(
        Long userId,
        // 지금 남아 있는 크레딧. 환불은 이 값이 그 충전의 크레딧 이상일 때만 된다
        int balance,
        // 결제된 적이 있는 충전만, 최근 것부터 (환불 중 · 환불된 것 포함)
        List<Item> payments
) {

    public record Item(
            // 환불 API에 넣는 주문번호
            String orderId,
            int amount,
            int credits,
            PaymentStatus status,
            LocalDateTime paidAt,
            LocalDateTime cancelledAt,
            // 지금 전액 환불을 요청할 수 있는지 (결제 완료 상태이고 잔액이 충분하다).
            // 여러 건이 true여도 한 건을 환불하면 잔액이 줄어 나머지는 false가 될 수 있다
            boolean refundable
    ) {

        static Item of(Payment payment, int balance) {
            return new Item(
                    payment.getOrderId(),
                    payment.getAmount(),
                    payment.getCreditAmount(),
                    payment.getStatus(),
                    payment.getPaidAt(),
                    payment.getCancelledAt(),
                    payment.getStatus() == PaymentStatus.PAID && balance >= payment.getCreditAmount()
            );
        }
    }

    public static AdminPaymentSearchResponseDto of(Long userId, int balance, List<Payment> payments) {
        return new AdminPaymentSearchResponseDto(
                userId, balance, payments.stream().map(payment -> Item.of(payment, balance)).toList());
    }
}
