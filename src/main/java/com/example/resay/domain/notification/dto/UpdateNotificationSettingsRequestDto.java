package com.example.resay.domain.notification.dto;

// 바꿀 항목만 보낸다. 보내지 않은(null) 항목은 그대로 둔다
public record UpdateNotificationSettingsRequestDto(
        Boolean analysis,
        Boolean service,
        // 약관의 마케팅 수신 동의와 같은 값이다 (PATCH /api/v1/users/me/agreements와 같은 결과)
        Boolean marketing
) {
}
