package com.example.resay.global.infrastructure.liner;

import lombok.Getter;

@Getter
public class LinerAnalysisException extends RuntimeException {

    private final String errorCode;

    public LinerAnalysisException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
