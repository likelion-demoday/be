package com.example.resay.global.infrastructure.clova;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 비동기 요청 접수 응답 (전사 결과는 callback으로 따로 전달된다)
@JsonIgnoreProperties(ignoreUnknown = true)
public record ClovaSpeechJobResponse(
        String result,
        String message,
        String token
) {
}
