package com.example.resay.domain.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 메일 안의 버튼이 여는 프론트 주소 (운영: https://resay.site)
@ConfigurationProperties(prefix = "app")
public record NotificationMailProperties(String frontendBaseUrl) {

    private static final String DEFAULT_FRONTEND_BASE_URL = "http://localhost:5173";

    public NotificationMailProperties {
        frontendBaseUrl = frontendBaseUrl == null || frontendBaseUrl.isBlank()
                ? DEFAULT_FRONTEND_BASE_URL
                : frontendBaseUrl.replaceAll("/+$", "");
    }
}
