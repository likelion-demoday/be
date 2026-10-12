package com.example.resay.domain.payment.dto;

import com.example.resay.domain.payment.entity.Payment;
import com.example.resay.domain.payment.entity.PaymentStatus;
import java.time.LocalDateTime;

public record PaymentResponseDto(
        String orderId,
        String productCode,
        // 결제 금액(원)
        int amount,
        // 지급 크레딧
        int credits,
        // READY: 결제 전(결제창을 닫은 주문 포함). APPROVING: 결제 결과를 확인하는 중 → 몇 초 간격으로 다시 조회한다.
        // 대부분 바로 PAID · FAILED가 되지만, 승인 응답을 받지 못한 주문은 확인에 몇 분 걸린다
        PaymentStatus status,
        LocalDateTime paidAt,
        // 카드 매출전표 주소. 결제된 주문에만 있다
        String receiptUrl,
        // 결제되지 않은 이유 (카드사 · 나이스페이가 보낸 안내 문구). 없을 수 있다. 외부에서 온 글이므로 텍스트로만 표시한다
        String failMessage,
        LocalDateTime createdAt
) {

    public static PaymentResponseDto from(Payment payment) {
        return new PaymentResponseDto(
                payment.getOrderId(),
                payment.getProductCode(),
                payment.getAmount(),
                payment.getCreditAmount(),
                payment.getStatus(),
                payment.getPaidAt(),
                payment.getReceiptUrl(),
                payment.getFailMessage(),
                payment.getCreatedAt()
        );
    }
}
