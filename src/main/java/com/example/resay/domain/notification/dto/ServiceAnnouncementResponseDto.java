package com.example.resay.domain.notification.dto;

// targetCount = sentCount + failedCount. 발송 설정(API 키)이 없어 로그만 남긴 건은 sentCount에 들어간다
public record ServiceAnnouncementResponseDto(
        int targetCount,
        int sentCount,
        int failedCount
) {
}
