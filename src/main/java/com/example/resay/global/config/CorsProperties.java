package com.example.resay.global.config;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(
        @NotEmpty(message = "cors.allowed-origins가 비어 있습니다. CORS_ALLOWED_ORIGINS 환경변수를 설정하세요.")
        List<String> allowedOrigins
) {
}
