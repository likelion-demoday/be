package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.ConversationMetrics;
import com.example.resay.domain.analysis.model.SpeakerMetrics;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.example.resay.domain.analysis.support.AnalysisTestSpeakers.speakersFor;

class AnalysisReadinessValidatorTest {

    private final AnalysisReadinessValidator validator = new AnalysisReadinessValidator();

    @Test
    void acceptsMetricsForRequiredSpeakerRoles() {
        AnalysisSource source = source();
        ConversationMetrics metrics = new ConversationMetrics(Map.of(
                SpeakerRole.SELF, metrics(SpeakerRole.SELF),
                SpeakerRole.PARTNER, metrics(SpeakerRole.PARTNER)
        ));

        assertThatCode(() -> validator.validate(source, metrics)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMetricsForDifferentSpeakerRoles() {
        AnalysisSource source = source();
        ConversationMetrics metrics = new ConversationMetrics(Map.of(
                SpeakerRole.SELF, metrics(SpeakerRole.SELF),
                SpeakerRole.FRIEND, metrics(SpeakerRole.FRIEND)
        ));

        assertThatThrownBy(() -> validator.validate(source, metrics))
                .isInstanceOf(AnalysisReadinessException.class);
    }

    private AnalysisSource source() {
        return new AnalysisSource(
                1L,
                AnalysisScenario.COUPLE_DAILY,
                10_000L,
                speakersFor(AnalysisScenario.COUPLE_DAILY),
                List.of(
                        new AnalysisSegment(1L, SpeakerRole.SELF, 100L, 500L, "안녕"),
                        new AnalysisSegment(2L, SpeakerRole.PARTNER, 600L, 900L, "반가워")
                )
        );
    }

    private SpeakerMetrics metrics(SpeakerRole speakerRole) {
        return new SpeakerMetrics(
                speakerRole,
                1_000L,
                2,
                10L,
                BigDecimal.valueOf(50),
                BigDecimal.valueOf(500),
                BigDecimal.valueOf(600)
        );
    }
}
