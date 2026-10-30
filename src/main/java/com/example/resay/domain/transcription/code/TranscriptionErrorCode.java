package com.example.resay.domain.transcription.code;

import com.example.resay.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TranscriptionErrorCode implements BaseErrorCode {

    INVALID_CALLBACK(HttpStatus.BAD_REQUEST, "TRANSCRIPTION400_1", "전사 결과 형식이 올바르지 않습니다."),
    TRANSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "TRANSCRIPTION404_1", "해당 전사 요청을 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
