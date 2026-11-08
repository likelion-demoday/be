package com.example.resay.domain.notification.code;

import com.example.resay.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum NotificationErrorCode implements BaseErrorCode {

    // 알림 이메일 없이 알림을 켜려는 경우. 프론트는 개인정보 수정에서 이메일을 입력하도록 안내한다
    EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "NOTIFICATION400_1", "알림을 받을 이메일을 먼저 등록해 주세요."),
    // 서비스 알림 대상이 오늘 남은 발송 가능 수보다 많은 경우. 한 통도 보내지 않는다
    DAILY_QUOTA_EXCEEDED(HttpStatus.BAD_REQUEST, "NOTIFICATION400_2", "오늘 보낼 수 있는 메일 수를 넘었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
