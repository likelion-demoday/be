package com.example.resay.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequestDto(
        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(max = 20, message = "닉네임은 20자 이하여야 합니다.")
        String nickname
) {

    public UpdateUserRequestDto {
        nickname = nickname == null ? null : nickname.trim();
    }
}
