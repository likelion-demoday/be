package com.example.resay.global.infrastructure.mail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 오류 응답의 message에는 받는 사람 주소가 들어갈 수 있어 name(오류 종류)만 쓴다
@JsonIgnoreProperties(ignoreUnknown = true)
public record ResendErrorResponse(String name) {
}
