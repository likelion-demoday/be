package com.example.resay.domain.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateNotificationEmailRequestDto(
        // null이나 빈 값이면 알림 이메일을 지운다 (온보딩에서 건너뛴 경우 포함)
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        // @Email은 a@b처럼 도메인에 점이 없는 주소도 통과시키므로, 실제로 받을 수 있는 주소만 남긴다
        @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 100, message = "이메일은 100자 이하여야 합니다.")
        String email
) {

    // 복사·붙여넣기로 붙은 앞뒤 공백 때문에 형식 검증에서 떨어지지 않도록 검증 전에 제거하고, 빈 값은 삭제로 본다
    public UpdateNotificationEmailRequestDto {
        email = email == null || email.isBlank() ? null : email.trim();
    }
}
