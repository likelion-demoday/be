package com.example.resay.domain.analysis.config;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analysis.readiness")
public record AnalysisReadinessProperties(
        Duration minimumTotalSpeakingDuration,
        Duration minimumSpeakerSpeakingDuration,
        long minimumSpeakerCharacterCount,
        BigDecimal minimumSpeakerRatioPercent
) {

    public AnalysisReadinessProperties {
        if (minimumTotalSpeakingDuration == null || minimumTotalSpeakingDuration.isNegative()
                || minimumTotalSpeakingDuration.isZero()) {
            throw new IllegalArgumentException("minimumTotalSpeakingDuration은 양수여야 합니다.");
        }
        if (minimumSpeakerSpeakingDuration == null || minimumSpeakerSpeakingDuration.isNegative()
                || minimumSpeakerSpeakingDuration.isZero()) {
            throw new IllegalArgumentException("minimumSpeakerSpeakingDuration은 양수여야 합니다.");
        }
        if (minimumSpeakerCharacterCount <= 0) {
            throw new IllegalArgumentException("minimumSpeakerCharacterCount는 양수여야 합니다.");
        }
        if (minimumSpeakerRatioPercent == null
                || minimumSpeakerRatioPercent.signum() <= 0
                || minimumSpeakerRatioPercent.compareTo(BigDecimal.valueOf(50)) > 0) {
            throw new IllegalArgumentException("minimumSpeakerRatioPercent는 0 초과 50 이하여야 합니다.");
        }
    }
}
