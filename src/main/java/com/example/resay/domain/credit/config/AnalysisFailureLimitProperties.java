package com.example.resay.domain.credit.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 분석이 실패하면 크레딧 · 무료 기회가 되살아나므로, 실패하는 녹음을 반복해 올리면 전사 · 분석 비용만 계속 나간다.
 * window 동안 실패로 환급된 분석이 maxFailures건이 되면 그중 가장 오래된 건이 window를 벗어날 때까지 새 분석 결제를 막는다.
 */
@Validated
@ConfigurationProperties(prefix = "credit.analysis-failure-limit")
public record AnalysisFailureLimitProperties(
        @Min(1) int maxFailures,
        @NotNull Duration window
) {
}
