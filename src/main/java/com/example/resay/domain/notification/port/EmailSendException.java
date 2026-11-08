package com.example.resay.domain.notification.port;

// 메일 서비스가 거절했거나 응답을 받지 못한 경우.
// reason에는 받는 사람 주소 같은 개인정보가 들어가지 않게 상태 코드와 오류 종류만 담는다
public class EmailSendException extends RuntimeException {

    private final String reason;

    public EmailSendException(String reason, Throwable cause) {
        super("메일 발송 실패: " + reason, cause);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
