package com.example.resay.domain.notification.dto;

// 서비스 알림 대상이 오늘 남은 발송 가능 수보다 많을 때 응답의 error에 담는다
public record DailyQuotaExceededDetail(
        int targetCount,
        int remainingToday
) {
}
