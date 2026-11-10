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
}
