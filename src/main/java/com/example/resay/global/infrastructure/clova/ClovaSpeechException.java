package com.example.resay.global.infrastructure.clova;

import lombok.Getter;

@Getter
public class ClovaSpeechException extends RuntimeException {

    private final Integer statusCode;

    public ClovaSpeechException(String message, Integer statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }
}
