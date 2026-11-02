package com.example.resay.domain.analysis.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 보고서와 개인화 요약에서 공통으로 사용하는 한국어 발화 속도 기준입니다.
 */
public final class SpeakingSpeedReference {

    public static final BigDecimal SYLLABLES_PER_SECOND = new BigDecimal("5.79");

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 2;

    private SpeakingSpeedReference() {
    }

    public static BigDecimal differencePercent(BigDecimal syllablesPerSecond) {
        if (syllablesPerSecond == null || syllablesPerSecond.signum() < 0) {
            throw new IllegalArgumentException("초당 음절 수는 0 이상이어야 합니다.");
        }
        return syllablesPerSecond
                .subtract(SYLLABLES_PER_SECOND)
                .multiply(ONE_HUNDRED)
                .divide(SYLLABLES_PER_SECOND, SCALE, RoundingMode.HALF_UP);
    }
}
