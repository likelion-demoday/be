package com.example.resay.global.exception;

import com.example.resay.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;

@Getter
public class GeneralException extends RuntimeException {

    private final BaseErrorCode errorCode;
    // 응답의 error 항목에 실어 보낼 부가 정보 (없으면 null)
    private final Object detail;

    public GeneralException(BaseErrorCode errorCode) {
        this(errorCode, null);
    }

    public GeneralException(BaseErrorCode errorCode, Object detail) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.detail = detail;
    }
}
