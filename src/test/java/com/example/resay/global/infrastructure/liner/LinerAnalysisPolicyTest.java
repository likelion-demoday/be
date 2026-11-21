package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class LinerAnalysisPolicyTest {

    @ParameterizedTest
    @MethodSource("requiredPercentMetrics")
    void definesCommonAndScenarioPercentMetricsForEveryScenario(
            AnalysisScenario scenario,
            List<QualitativeAnalysis.ReportMetricType> expectedScenarioTypes
    ) {
        assertThat(LinerAnalysisPolicy.requiredPercentReportMetricTypes(scenario))
                .containsExactly(
                        QualitativeAnalysis.ReportMetricType.SENTENCE_COMPLETENESS,
                        QualitativeAnalysis.ReportMetricType.ELABORATION,
                        QualitativeAnalysis.ReportMetricType.CLARITY,
                        QualitativeAnalysis.ReportMetricType.SENTENCE_VARIETY,
                        expectedScenarioTypes.get(0),
                        expectedScenarioTypes.get(1),
                        expectedScenarioTypes.get(2),
                        expectedScenarioTypes.get(3)
                );
    }

    @ParameterizedTest
    @MethodSource("requiredCountMetrics")
    void definesScenarioCountMetricsForEveryScenario(
            AnalysisScenario scenario,
            List<QualitativeAnalysis.ReportMetricType> expectedTypes
    ) {
        assertThat(LinerAnalysisPolicy.countReportMetricTypes(scenario))
                .containsExactlyInAnyOrderElementsOf(expectedTypes);
    }

    private static Stream<Arguments> requiredPercentMetrics() {
        return Stream.of(
                Arguments.of(
                        AnalysisScenario.FRIEND_DAILY,
                        List.of(
                                QualitativeAnalysis.ReportMetricType.SWEAR_WORD,
                                QualitativeAnalysis.ReportMetricType.DIRECTNESS,
                                QualitativeAnalysis.ReportMetricType.EXPRESSION_INTENSITY,
                                QualitativeAnalysis.ReportMetricType.SOFTENING_EXPRESSION
                        )
                ),
                Arguments.of(
                        AnalysisScenario.COUPLE_DAILY,
                        List.of(
                                QualitativeAnalysis.ReportMetricType.DIRECT_AFFECTION,
                                QualitativeAnalysis.ReportMetricType.CARING_EXPRESSION,
                                QualitativeAnalysis.ReportMetricType.COMPLIMENT_OR_ENCOURAGEMENT,
                                QualitativeAnalysis.ReportMetricType.AFFECTIONATE_ADDRESS
                        )
                ),
                Arguments.of(
                        AnalysisScenario.COUPLE_CONFLICT,
                        List.of(
                                QualitativeAnalysis.ReportMetricType.DIRECT_EXPRESSION,
                                QualitativeAnalysis.ReportMetricType.ASSERTIVE_EXPRESSION,
                                QualitativeAnalysis.ReportMetricType.SOFTENING_EXPRESSION,
                                QualitativeAnalysis.ReportMetricType.RECOVERY_EXPRESSION
                        )
                ),
                Arguments.of(
                        AnalysisScenario.PARENT_CHILD_CONFLICT,
                        List.of(
                                QualitativeAnalysis.ReportMetricType.ADVICE,
                                QualitativeAnalysis.ReportMetricType.ASSERTIVE_EXPRESSION,
                                QualitativeAnalysis.ReportMetricType.EMOTION_CHECK,
                                QualitativeAnalysis.ReportMetricType.SOFTENING_EXPRESSION
                        )
                )
        );
    }

    private static Stream<Arguments> requiredCountMetrics() {
        return Stream.of(
                Arguments.of(
                        AnalysisScenario.FRIEND_DAILY,
                        List.of(
                                QualitativeAnalysis.ReportMetricType.COMPLIMENT,
                                QualitativeAnalysis.ReportMetricType.ENCOURAGEMENT,
                                QualitativeAnalysis.ReportMetricType.CARING_EXPRESSION,
                                QualitativeAnalysis.ReportMetricType.FRIENDLY_ADDRESS,
                                QualitativeAnalysis.ReportMetricType.DIRECT_AFFECTION,
                                QualitativeAnalysis.ReportMetricType.JOY_OR_GRATITUDE
                        )
                ),
                Arguments.of(
                        AnalysisScenario.COUPLE_DAILY,
                        List.of(
                                QualitativeAnalysis.ReportMetricType.COMPLIMENT,
                                QualitativeAnalysis.ReportMetricType.ENCOURAGEMENT,
                                QualitativeAnalysis.ReportMetricType.CARING_EXPRESSION,
                                QualitativeAnalysis.ReportMetricType.DIRECT_AFFECTION,
                                QualitativeAnalysis.ReportMetricType.JOY_OR_GRATITUDE
                        )
                ),
                Arguments.of(
                        AnalysisScenario.COUPLE_CONFLICT,
                        List.of(
                                QualitativeAnalysis.ReportMetricType.POSITION_EXPLANATION,
                                QualitativeAnalysis.ReportMetricType.REBUTTAL,
                                QualitativeAnalysis.ReportMetricType.AVOIDANCE,
                                QualitativeAnalysis.ReportMetricType.APOLOGY,
                                QualitativeAnalysis.ReportMetricType.YIELDING,
                                QualitativeAnalysis.ReportMetricType.EMOTION_CHECK,
                                QualitativeAnalysis.ReportMetricType.QUESTION,
                                QualitativeAnalysis.ReportMetricType.MISSED_RESPONSE
                        )
                ),
                Arguments.of(
                        AnalysisScenario.PARENT_CHILD_CONFLICT,
                        List.of(
                                QualitativeAnalysis.ReportMetricType.SITUATION_QUESTION,
                                QualitativeAnalysis.ReportMetricType.CONCRETE_ADVICE,
                                QualitativeAnalysis.ReportMetricType.EMOTION_CHECK,
                                QualitativeAnalysis.ReportMetricType.ASSERTIVE_EXPRESSION,
                                QualitativeAnalysis.ReportMetricType.EXPERIENCE_COMPARISON,
                                QualitativeAnalysis.ReportMetricType.SHORT_RESPONSE,
                                QualitativeAnalysis.ReportMetricType.TOPIC_AVOIDANCE,
                                QualitativeAnalysis.ReportMetricType.REBUTTAL,
                                QualitativeAnalysis.ReportMetricType.EMOTION_EXPRESSION,
                                QualitativeAnalysis.ReportMetricType.APOLOGY,
                                QualitativeAnalysis.ReportMetricType.CHILD_EXPRESSION,
                                QualitativeAnalysis.ReportMetricType.PARENT_CONFIRMATION
                        )
                )
        );
    }
}
