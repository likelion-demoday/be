package com.example.resay.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequestDto(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
        String email,

        // BCrypt는 72바이트까지만 사용하므로 상한을 둔다
        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
        String password,

        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(max = 20, message = "닉네임은 20자 이하여야 합니다.")
        String nickname
) {

    // 복사·붙여넣기로 붙은 앞뒤 공백 때문에 형식 검증에서 떨어지지 않도록 검증 전에 제거한다
    public SignupRequestDto {
        email = trim(email);
        nickname = trim(nickname);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
