package com.example.resay.domain.payment.dto;

/**
 * 결제창(AUTHNICE.requestPay)에 그대로 넘길 값.
 * 프론트는 이 값을 바꾸지 않는다. 금액 · 주문번호가 주문과 다르면 승인하지 않는다.
 */
public record PaymentOrderResponseDto(
        String orderId,
        int amount,
        String goodsName,
        String clientId,
        String method,
        // 결제창이 인증 결과를 보내는 백엔드 주소
        String returnUrl
) {
}
