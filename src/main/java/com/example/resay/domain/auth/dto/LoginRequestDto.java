package com.example.resay.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequestDto(
        @NotBlank(message = "이메일을 입력해 주세요.")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password
) {

    public LoginRequestDto {
        email = email == null ? null : email.trim();
    }
}
