package com.example.resay.global.security.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
        boolean enabled,
        @NotNull @Valid Login login,
        @NotNull @Valid Signup signup
) {

    public record Login(
            @Min(1) int maxFailuresPerIp,
            @Min(1) int maxFailuresPerEmail,
            @NotNull Duration window
    ) {
    }

    public record Signup(
            @Min(1) int maxAttemptsPerIp,
            @NotNull Duration window
    ) {
    }
}
