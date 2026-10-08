package com.example.resay.domain.payment.dto;

/**
 * 결제창이 인증을 마친 뒤 브라우저를 통해 form으로 보내는 값 (나이스페이가 정한 이름 그대로).
 * 로그인 없이 받는 값이라 그대로 믿지 않고 주문 · 서명과 대조한다.
 *
 * @param authResultCode 0000이면 인증 성공. 그 외에는 승인을 요청하지 않는다
 * @param tid            승인 요청에 쓰는 거래 키 (인증에 성공했을 때만 온다)
 * @param amount         결제 금액
 * @param authToken      서명 검증에 쓰는 값
 * @param signature      hex(sha256(authToken + clientId + amount + 시크릿 키)) (인증에 성공했을 때만 온다)
 */
public record NicepayReturnRequestDto(
        String authResultCode,
        String authResultMsg,
        String tid,
        String clientId,
        String orderId,
        String amount,
        String authToken,
        String signature
) {

    // 인증 토큰과 서명이 로그에 찍히지 않게 한다
    @Override
    public String toString() {
        return "NicepayReturnRequestDto[orderId=" + orderId + ", authResultCode=" + authResultCode + "]";
    }
}
