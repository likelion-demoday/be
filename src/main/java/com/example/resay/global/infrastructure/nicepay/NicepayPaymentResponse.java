package com.example.resay.global.infrastructure.nicepay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 나이스페이 응답 중 쓰는 항목만 받는다. 카드 번호 · 구매자 정보 같은 나머지 항목은 읽지 않는다
@JsonIgnoreProperties(ignoreUnknown = true)
record NicepayPaymentResponse(
        String resultCode,
        String resultMsg,
        String tid,
        String orderId,
        String ediDate,
        String signature,
        String status,
        // ISO 8601 형식. 결제 완료가 아니면 "0"
        String paidAt,
        Integer amount,
        String receiptUrl
) {
}
