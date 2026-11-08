package com.example.resay.domain.notification.repository;

import java.time.LocalDateTime;

// 마케팅 리마인드를 받을 수 있는 사용자 (마케팅 수신 동의 + 알림 이메일 있음)와 주기 계산에 쓰는 시각들
public record ReminderCandidate(
        Long userId,
        String nickname,
        String email,
        LocalDateTime signedUpAt,
        // 녹음한 적이 없으면 null
        LocalDateTime lastRecordedAt,
        // 리마인드를 받은 적이 없으면 null (발송 실패는 받은 것으로 치지 않는다)
        LocalDateTime lastRemindedAt
) {
}
