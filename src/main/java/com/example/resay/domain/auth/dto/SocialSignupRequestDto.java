package com.example.resay.domain.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SocialSignupRequestDto(
        // 소셜 로그인 응답에서 받은 가입용 임시 토큰
        @NotBlank(message = "registerToken을 입력해 주세요.")
        String registerToken,

        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(max = 20, message = "닉네임은 20자 이하여야 합니다.")
        String nickname,

        @NotNull(message = "약관 동의 항목을 보내 주세요.")
        @Valid
        AgreementsDto agreements
) {

    public SocialSignupRequestDto {
        nickname = nickname == null ? null : nickname.trim();
    }
}
