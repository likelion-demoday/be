package com.example.resay.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequestDto(
        @NotBlank(message = "이메일을 입력해 주세요.")
        // 가입할 수 있는 최대 길이. 이보다 긴 값은 가입된 계정일 수 없다
        @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password
) {

    public LoginRequestDto {
        email = email == null ? null : email.trim();
    }
}
