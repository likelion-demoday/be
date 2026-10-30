package com.example.resay.domain.transcription.code;

import com.example.resay.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TranscriptionSuccessCode implements BaseSuccessCode {

    CALLBACK_RECEIVED(HttpStatus.OK, "TRANSCRIPTION200_1", "전사 결과를 받았습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
