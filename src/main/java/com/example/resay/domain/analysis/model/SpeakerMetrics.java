package com.example.resay.domain.analysis.model;

import java.math.BigDecimal;

// 화자별 계산 결과
public record SpeakerMetrics(
        SpeakerRole speakerRole,
        long speakingDurationMs,
        int utteranceCount,
        long transcribedCharacterCount,
        BigDecimal speakingRatioPercent,
        BigDecimal averageUtteranceDurationMs,
        BigDecimal charactersPerMinute
) {

    public SpeakerMetrics {
        if (speakerRole == null) {
            throw new IllegalArgumentException("speakerRole은 비어 있을 수 없습니다.");
        }
        if (speakingDurationMs <= 0) {
            throw new IllegalArgumentException("speakingDurationMs는 양수여야 합니다.");
        }
        if (utteranceCount <= 0) {
            throw new IllegalArgumentException("utteranceCount는 양수여야 합니다.");
        }
        if (transcribedCharacterCount < 0) {
            throw new IllegalArgumentException("transcribedCharacterCount는 0 이상이어야 합니다.");
        }
        requireRange(speakingRatioPercent, "speakingRatioPercent", BigDecimal.ZERO, BigDecimal.valueOf(100));
        requirePositive(averageUtteranceDurationMs, "averageUtteranceDurationMs");
        requireNonNegative(charactersPerMinute, "charactersPerMinute");
    }

    private static void requireRange(
            BigDecimal value,
            String fieldName,
            BigDecimal minimum,
            BigDecimal maximum
    ) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(fieldName + "의 범위가 올바르지 않습니다.");
        }
    }

    private static void requirePositive(BigDecimal value, String fieldName) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(fieldName + "은(는) 양수여야 합니다.");
        }
    }

    private static void requireNonNegative(BigDecimal value, String fieldName) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(fieldName + "은(는) 0 이상이어야 합니다.");
        }
    }
}
