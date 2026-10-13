package com.example.resay.domain.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 나이스페이가 결제 · 취소가 일어났을 때 서버로 보내는 통보(웹훅) 중 쓰는 항목만 받는다 (나이스페이가 정한 이름 그대로).
 * 카드 번호 · 구매자 정보 같은 나머지 항목은 읽지 않는다.
 * 로그인 없이 받는 값이라 그대로 믿지 않는다. 서명을 확인하고, 주문 상태는 나이스페이에 조회한 결과로만 바꾼다.
 *
 * @param tid       처음 결제된 거래의 키. 취소 통보에도 원래 거래의 키가 온다
 * @param status    paid, cancelled, partialCancelled 등
 * @param amount    결제 금액 (취소 통보에서도 원래 결제 금액)
 * @param signature hex(sha256(tid + amount + ediDate + 시크릿 키))
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NicepayWebhookRequestDto(
        String tid,
        String status,
        Integer amount,
        String ediDate,
        String signature
) {

    // 서명이 로그에 찍히지 않게 한다
    @Override
    public String toString() {
        return "NicepayWebhookRequestDto[tid=" + tid + ", status=" + status + "]";
    }
}
