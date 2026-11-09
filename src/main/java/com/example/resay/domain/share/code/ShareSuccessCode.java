package com.example.resay.domain.share.code;

import com.example.resay.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ShareSuccessCode implements BaseSuccessCode {

    CREATED(HttpStatus.CREATED, "SHARE201_1", "공유 링크를 발급했습니다."),
    DELETED(HttpStatus.OK, "SHARE200_1", "공유 링크를 취소했습니다."),
    REPORT_GET(HttpStatus.OK, "SHARE200_2", "공유 보고서를 조회했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
