package com.example.resay.domain.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// 미사용자 마케팅 리마인드 설정. 운영 기간(~11/21)에는 7일 주기, 이후에는 interval-days만 늘려 재배포한다
@ConfigurationProperties(prefix = "notification.reminder")
public record MarketingReminderProperties(
        Boolean enabled,
        // 마지막 녹음(없으면 가입) 또는 마지막 리마인드 이후 이만큼 지나면 보낸다
        Integer intervalDays,
        // 하루 리마인드 상한. Resend 무료 하루 100통 중 분석 완료 메일 몫을 남긴다
        Integer dailyLimit,
        // Resend 기본 요청 한도(초당 2건)를 넘지 않게 메일 사이에 쉬는 시간
        Duration sendInterval,
        // 광고 메일에 반드시 표시하는 전송자 연락처
        String contactEmail
) {

    private static final int DEFAULT_INTERVAL_DAYS = 7;
    private static final int DEFAULT_DAILY_LIMIT = 80;
    private static final Duration DEFAULT_SEND_INTERVAL = Duration.ofMillis(600);
    private static final String DEFAULT_CONTACT_EMAIL = "hoslee404@gmail.com";

    public MarketingReminderProperties {
        enabled = enabled == null || enabled;
        intervalDays = intervalDays != null ? intervalDays : DEFAULT_INTERVAL_DAYS;
        dailyLimit = dailyLimit != null ? dailyLimit : DEFAULT_DAILY_LIMIT;
        sendInterval = sendInterval != null ? sendInterval : DEFAULT_SEND_INTERVAL;
        contactEmail = contactEmail != null && !contactEmail.isBlank() ? contactEmail : DEFAULT_CONTACT_EMAIL;
        if (intervalDays < 1) {
            throw new IllegalArgumentException("리마인드 주기는 1일 이상이어야 합니다.");
        }
        if (dailyLimit < 0) {
            throw new IllegalArgumentException("하루 리마인드 상한은 0 이상이어야 합니다.");
        }
    }
}
