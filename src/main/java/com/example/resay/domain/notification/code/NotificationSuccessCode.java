package com.example.resay.domain.notification.code;

import com.example.resay.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum NotificationSuccessCode implements BaseSuccessCode {

    GET_SETTINGS(HttpStatus.OK, "NOTIFICATION200_1", "알림 설정을 조회했습니다."),
    UPDATE_SETTINGS(HttpStatus.OK, "NOTIFICATION200_2", "알림 설정을 변경했습니다."),
    UPDATE_EMAIL(HttpStatus.OK, "NOTIFICATION200_3", "알림 이메일을 변경했습니다."),
    SEND_SERVICE_ANNOUNCEMENT(HttpStatus.OK, "NOTIFICATION200_4", "서비스 알림 메일을 보냈습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
