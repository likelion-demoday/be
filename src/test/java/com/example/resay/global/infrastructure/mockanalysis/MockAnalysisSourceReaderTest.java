package com.example.resay.global.infrastructure.mockanalysis;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.SpeakingSpeedReference;
import com.example.resay.domain.analysis.service.ConversationMetricsCalculator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class MockAnalysisSourceReaderTest {

    private final MockAnalysisSourceReader sourceReader =
            new MockAnalysisSourceReader(new ObjectMapper());
    private final ConversationMetricsCalculator metricsCalculator =
            new ConversationMetricsCalculator();

    @Test
    void keepsMockSpeakingSpeedsPositiveAndVariedAroundResearchReference() {
        List<BigDecimal> speakingSpeeds = new ArrayList<>();

        for (AnalysisScenario scenario : AnalysisScenario.values()) {
            AnalysisSource source = sourceReader.create(scenario);

            metricsCalculator.calculate(source).speakerMetrics().values().stream()
                    .map(metrics -> metrics.syllablesPerSecond())
                    .forEach(speakingSpeeds::add);

            for (int index = 0; index < source.segments().size() - 1; index++) {
                assertThat(source.segments().get(index).endMs())
                        .as("%s 목 발화 구간은 다음 발화와 겹치지 않아야 한다", scenario)
                        .isLessThanOrEqualTo(source.segments().get(index + 1).startMs());
            }
        }

        assertThat(speakingSpeeds).allSatisfy(speed -> assertThat(speed).isPositive());
        assertThat(speakingSpeeds)
                .anySatisfy(speed -> assertThat(speed)
                        .isLessThan(SpeakingSpeedReference.SYLLABLES_PER_SECOND))
                .anySatisfy(speed -> assertThat(speed)
                        .isGreaterThan(SpeakingSpeedReference.SYLLABLES_PER_SECOND));
    }
}
