package com.example.resay.domain.character.service;

public class CharacterDesignGenerationException extends RuntimeException {

    private final String errorCode;

    public CharacterDesignGenerationException(
            String errorCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
