package com.example.resay.domain.analysis.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analysis.stall")
public record AnalysisStallProperties(Duration timeout) {

    public AnalysisStallProperties {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout은 양수여야 합니다.");
        }
    }
}
