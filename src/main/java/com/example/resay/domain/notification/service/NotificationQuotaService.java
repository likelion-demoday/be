package com.example.resay.domain.notification.service;

import com.example.resay.domain.notification.entity.NotificationLogStatus;
import com.example.resay.domain.notification.repository.NotificationLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

// 메일 서비스의 하루 발송 한도(Resend 무료 100통)를 모든 알림(분석 완료 · 리마인드 · 서비스 알림)이 나눠 쓴다.
// 오늘 0시 이후 메일 서비스에 보낸(또는 보내는 중인) 통수를 발송 기록으로 센다. 거절된 발송은 한도에 들어가지 않는다
@Service
public class NotificationQuotaService {

    private final NotificationLogRepository notificationLogRepository;
    private final int dailyQuota;

    public NotificationQuotaService(
            NotificationLogRepository notificationLogRepository,
            @Value("${notification.daily-quota:100}") int dailyQuota
    ) {
        if (dailyQuota < 0) {
            throw new IllegalArgumentException("하루 발송 한도는 0 이상이어야 합니다.");
        }
        this.notificationLogRepository = notificationLogRepository;
        this.dailyQuota = dailyQuota;
    }

    // 리마인드처럼 미뤄도 되는 메일은 그날 남은 분석 완료 메일 몫을 남기고 보낸다
    static final int ANALYSIS_RESERVE = 20;

    // 미뤄도 되는 메일(리마인드)이 쓸 수 있는 남은 통수
    public int remainingForDeferrable(LocalDateTime now) {
        return Math.max(0, remainingToday(now) - ANALYSIS_RESERVE);
    }

    public int remainingToday(LocalDateTime now) {
        long usedToday = notificationLogRepository.countByCreatedAtGreaterThanEqualAndStatusNot(
                now.toLocalDate().atStartOfDay(), NotificationLogStatus.FAILED);
        return (int) Math.max(0, dailyQuota - usedToday);
    }
}
