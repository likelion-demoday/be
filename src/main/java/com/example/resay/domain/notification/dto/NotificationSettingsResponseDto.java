package com.example.resay.domain.notification.dto;

public record NotificationSettingsResponseDto(
        // 알림 받을 이메일. 등록하지 않았으면 null이고, 이때 프론트는 알림을 켜기 전에 이메일 입력을 안내한다
        String email,
        boolean analysis,
        boolean service,
        boolean marketing
) {
}
