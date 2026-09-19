package com.example.resay.global.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank(message = "jwt.secret이 비어 있습니다. 로컬은 application-local.yml, 운영은 JWT_SECRET 환경변수를 설정하세요.")
        String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenExpiration
) {
}
