package com.example.resay.global.infrastructure.nicepay;

/**
 * 나이스페이의 답을 받지 못해 요청이 처리됐는지 알 수 없는 경우 (타임아웃, 연결 끊김, 서버 오류, 읽을 수 없는 응답).
 * 승인 요청에서 이 예외가 나면 결제됐을 수도 있으므로 실패로 단정하지 않는다.
 */
public class NicepayUnknownResultException extends RuntimeException {

    public NicepayUnknownResultException(String message, Throwable cause) {
        super(message, cause);
    }
}
