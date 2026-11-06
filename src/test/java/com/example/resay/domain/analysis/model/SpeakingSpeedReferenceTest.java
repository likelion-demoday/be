package com.example.resay.domain.analysis.model;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpeakingSpeedReferenceTest {

    @Test
    void calculatesEachSpeakerDifferenceFromTheSameReference() {
        assertThat(SpeakingSpeedReference.differencePercent(new BigDecimal("6.50")))
                .isEqualByComparingTo("12.26");
        assertThat(SpeakingSpeedReference.differencePercent(new BigDecimal("6.10")))
                .isEqualByComparingTo("5.35");
        assertThat(SpeakingSpeedReference.differencePercent(new BigDecimal("5.00")))
                .isEqualByComparingTo("-13.64");
    }

    @Test
    void keepsDirectionForBackwardCompatibility() {
        assertThat(SpeakingSpeedReference.direction(new BigDecimal("5.00")))
                .isEqualTo(SpeakingSpeedDirection.SLOWER);
        assertThat(SpeakingSpeedReference.direction(new BigDecimal("5.79")))
                .isEqualTo(SpeakingSpeedDirection.SIMILAR);
        assertThat(SpeakingSpeedReference.direction(new BigDecimal("6.10")))
                .isEqualTo(SpeakingSpeedDirection.FASTER);
    }

    @Test
    void rejectsInvalidSpeakingSpeed() {
        assertThatThrownBy(() -> SpeakingSpeedReference.differencePercent(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpeakingSpeedReference.differencePercent(
                new BigDecimal("-0.01")
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
