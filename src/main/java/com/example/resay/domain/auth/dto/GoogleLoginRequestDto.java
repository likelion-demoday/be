package com.example.resay.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginRequestDto(
        // Google Identity Services 로그인 버튼이 돌려주는 credential 값
        @NotBlank(message = "ID 토큰을 입력해 주세요.")
        String idToken
) {
}
