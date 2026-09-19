package com.example.resay.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequestDto(
        @NotBlank(message = "리프레시 토큰을 입력해 주세요.")
        String refreshToken
) {
}
