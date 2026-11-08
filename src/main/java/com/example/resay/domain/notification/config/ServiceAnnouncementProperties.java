package com.example.resay.domain.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// 관리자 서비스 알림 설정. 하루 발송 한도는 모든 알림이 함께 쓴다 (notification.daily-quota)
@ConfigurationProperties(prefix = "notification.service")
public record ServiceAnnouncementProperties(
        // Resend 기본 요청 한도(초당 2건)를 넘지 않게 메일 사이에 쉬는 시간
        Duration sendInterval
) {

    private static final Duration DEFAULT_SEND_INTERVAL = Duration.ofMillis(600);

    public ServiceAnnouncementProperties {
        sendInterval = sendInterval != null ? sendInterval : DEFAULT_SEND_INTERVAL;
    }
}
