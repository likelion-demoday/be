package com.example.resay.domain.notification.entity;

public enum NotificationType {
    // 분석 완료 알림. 대상은 녹음 ID
    ANALYSIS_COMPLETED,
    // 미사용자 마케팅 리마인드 (광고). 사용자별로 주기마다 보내므로 대상 ID가 없다
    MARKETING_REMINDER,
    // 관리자가 보내는 서비스 알림 (점검 · 약관 변경 · 공지). 광고가 아니다
    SERVICE_ANNOUNCEMENT
}
