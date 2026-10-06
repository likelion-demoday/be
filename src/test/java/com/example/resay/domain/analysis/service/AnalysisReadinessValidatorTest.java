package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.config.AnalysisReadinessProperties;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.ConversationMetrics;
import com.example.resay.domain.analysis.model.SpeakerMetrics;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.example.resay.domain.analysis.support.AnalysisTestSpeakers.speakersFor;

class AnalysisReadinessValidatorTest {

    private final AnalysisReadinessValidator validator = new AnalysisReadinessValidator(
            new AnalysisReadinessProperties(
                    Duration.ofSeconds(60),
                    Duration.ofSeconds(15),
                    30,
                    BigDecimal.valueOf(5)
            )
    );

    @Test
    void acceptsMetricsForRequiredSpeakerRoles() {
        AnalysisSource source = source();
        ConversationMetrics metrics = new ConversationMetrics(Map.of(
                SpeakerRole.SELF, metrics(SpeakerRole.SELF, 35_000L, 40L, 50),
                SpeakerRole.PARTNER, metrics(SpeakerRole.PARTNER, 35_000L, 40L, 50)
        ));

        assertThatCode(() -> validator.validate(source, metrics)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMetricsForDifferentSpeakerRoles() {
        AnalysisSource source = source();
        ConversationMetrics metrics = new ConversationMetrics(Map.of(
                SpeakerRole.SELF, metrics(SpeakerRole.SELF, 35_000L, 40L, 50),
                SpeakerRole.FRIEND, metrics(SpeakerRole.FRIEND, 35_000L, 40L, 50)
        ));

        assertThatThrownBy(() -> validator.validate(source, metrics))
                .isInstanceOf(AnalysisReadinessException.class);
    }

    @Test
    void rejectsConversationWithTooLittleTotalSpeakingTime() {
        ConversationMetrics metrics = new ConversationMetrics(Map.of(
                SpeakerRole.SELF, metrics(SpeakerRole.SELF, 25_000L, 40L, 50),
                SpeakerRole.PARTNER, metrics(SpeakerRole.PARTNER, 25_000L, 40L, 50)
        ));

        assertThatThrownBy(() -> validator.validate(source(), metrics))
                .isInstanceOf(AnalysisReadinessException.class)
                .hasMessageContaining("전체 발화 시간");
    }

    @Test
    void rejectsSpeakerWithTooLittleSpeakingTime() {
        ConversationMetrics metrics = new ConversationMetrics(Map.of(
                SpeakerRole.SELF, metrics(SpeakerRole.SELF, 10_000L, 40L, 14),
                SpeakerRole.PARTNER, metrics(SpeakerRole.PARTNER, 60_000L, 40L, 86)
        ));

        assertThatThrownBy(() -> validator.validate(source(), metrics))
                .isInstanceOf(AnalysisReadinessException.class)
                .hasMessageContaining("발화 시간");
    }

    @Test
    void rejectsSpeakerWithTooLittleTranscribedText() {
        ConversationMetrics metrics = new ConversationMetrics(Map.of(
                SpeakerRole.SELF, metrics(SpeakerRole.SELF, 35_000L, 20L, 50),
                SpeakerRole.PARTNER, metrics(SpeakerRole.PARTNER, 35_000L, 40L, 50)
        ));

        assertThatThrownBy(() -> validator.validate(source(), metrics))
                .isInstanceOf(AnalysisReadinessException.class)
                .hasMessageContaining("전사량");
    }

    @Test
    void rejectsSpeakerWithTooLittleSpeakingRatio() {
        ConversationMetrics metrics = new ConversationMetrics(Map.of(
                SpeakerRole.SELF, metrics(SpeakerRole.SELF, 35_000L, 40L, 4),
                SpeakerRole.PARTNER, metrics(SpeakerRole.PARTNER, 35_000L, 40L, 96)
        ));

        assertThatThrownBy(() -> validator.validate(source(), metrics))
                .isInstanceOf(AnalysisReadinessException.class)
                .hasMessageContaining("발화 비율");
    }

    private AnalysisSource source() {
        return new AnalysisSource(
                1L,
                AnalysisScenario.COUPLE_DAILY,
                90_000L,
                speakersFor(AnalysisScenario.COUPLE_DAILY),
                List.of(
                        new AnalysisSegment(1L, SpeakerRole.SELF, 100L, 500L, "안녕"),
                        new AnalysisSegment(2L, SpeakerRole.PARTNER, 600L, 900L, "반가워")
                )
        );
    }

    private SpeakerMetrics metrics(
            SpeakerRole speakerRole,
            long speakingDurationMs,
            long transcribedCharacterCount,
            int speakingRatioPercent
    ) {
        return new SpeakerMetrics(
                speakerRole,
                speakingDurationMs,
                2,
                transcribedCharacterCount,
                transcribedCharacterCount,
                10,
                BigDecimal.valueOf(speakingRatioPercent),
                BigDecimal.valueOf(speakingDurationMs / 2.0),
                BigDecimal.valueOf(600),
                BigDecimal.ONE
        );
    }
}
