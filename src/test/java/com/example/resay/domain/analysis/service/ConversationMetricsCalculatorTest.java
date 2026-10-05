package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.ConversationMetrics;
import com.example.resay.domain.analysis.model.SpeakerMetrics;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.example.resay.domain.analysis.support.AnalysisTestSpeakers.speakersFor;

class ConversationMetricsCalculatorTest {

    private final ConversationMetricsCalculator calculator = new ConversationMetricsCalculator();

    @Test
    void calculatesMetricsFromDetectedSpeakingTime() {
        AnalysisSource source = new AnalysisSource(
                1L,
                AnalysisScenario.FRIEND_DAILY,
                70_000L,
                speakersFor(AnalysisScenario.FRIEND_DAILY),
                List.of(
                        new AnalysisSegment(1L, SpeakerRole.SELF, 0L, 30_000L, "안녕 친구야"),
                        new AnalysisSegment(2L, SpeakerRole.FRIEND, 30_000L, 50_000L, "응 잘 지냈어"),
                        new AnalysisSegment(3L, SpeakerRole.SELF, 50_000L, 60_000L, "잘 지냈어?")
                )
        );

        ConversationMetrics metrics = calculator.calculate(source);
        SpeakerMetrics self = metrics.metricsFor(SpeakerRole.SELF);
        SpeakerMetrics friend = metrics.metricsFor(SpeakerRole.FRIEND);

        assertThat(self.speakingDurationMs()).isEqualTo(40_000L);
        assertThat(self.utteranceCount()).isEqualTo(2);
        assertThat(self.transcribedCharacterCount()).isEqualTo(9L);
        assertThat(self.speakingRatioPercent()).isEqualByComparingTo("66.67");
        assertThat(self.averageUtteranceDurationMs()).isEqualByComparingTo("20000.00");
        assertThat(self.charactersPerMinute()).isEqualByComparingTo("13.50");

        assertThat(friend.speakingDurationMs()).isEqualTo(20_000L);
        assertThat(friend.utteranceCount()).isEqualTo(1);
        assertThat(friend.transcribedCharacterCount()).isEqualTo(5L);
        assertThat(friend.speakingRatioPercent()).isEqualByComparingTo("33.33");
        assertThat(friend.averageUtteranceDurationMs()).isEqualByComparingTo("20000.00");
        assertThat(friend.charactersPerMinute()).isEqualByComparingTo("15.00");

        assertThat(metrics.speakingSpeedComparison()).hasValueSatisfying(comparison -> {
            assertThat(comparison.fasterSpeakerRole()).isEqualTo(SpeakerRole.FRIEND);
            assertThat(comparison.slowerSpeakerRole()).isEqualTo(SpeakerRole.SELF);
            assertThat(comparison.percentDifference()).isEqualByComparingTo("11.11");
        });
    }

    @Test
    void excludesWhitespacePunctuationAndEmojiFromCharacterCount() {
        AnalysisSource source = new AnalysisSource(
                1L,
                AnalysisScenario.FRIEND_DAILY,
                120_000L,
                speakersFor(AnalysisScenario.FRIEND_DAILY),
                List.of(
                        new AnalysisSegment(1L, SpeakerRole.SELF, 0L, 60_000L, "안녕, 123! 😊"),
                        new AnalysisSegment(2L, SpeakerRole.FRIEND, 60_000L, 120_000L, "... 😊")
                )
        );

        ConversationMetrics metrics = calculator.calculate(source);

        assertThat(metrics.metricsFor(SpeakerRole.SELF).transcribedCharacterCount())
                .isEqualTo(5L);
        assertThat(metrics.metricsFor(SpeakerRole.SELF).charactersPerMinute())
                .isEqualByComparingTo("5.00");
        assertThat(metrics.metricsFor(SpeakerRole.FRIEND).transcribedCharacterCount())
                .isZero();
        assertThat(metrics.metricsFor(SpeakerRole.FRIEND).charactersPerMinute())
                .isEqualByComparingTo("0.00");
        assertThat(metrics.speakingSpeedComparison()).isEmpty();
    }

    @Test
    void exposesImmutableSpeakerMetrics() {
        ConversationMetrics metrics = calculator.calculate(simpleSource());

        assertThatThrownBy(() -> metrics.speakerMetrics().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsNullSource() {
        assertThatThrownBy(() -> calculator.calculate(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("분석 입력");
    }

    private AnalysisSource simpleSource() {
        return new AnalysisSource(
                1L,
                AnalysisScenario.COUPLE_DAILY,
                10_000L,
                speakersFor(AnalysisScenario.COUPLE_DAILY),
                List.of(
                        new AnalysisSegment(1L, SpeakerRole.SELF, 0L, 4_000L, "안녕"),
                        new AnalysisSegment(2L, SpeakerRole.PARTNER, 5_000L, 9_000L, "반가워")
                )
        );
    }
}
