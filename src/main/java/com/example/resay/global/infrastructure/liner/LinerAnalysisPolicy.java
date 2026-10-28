package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import java.util.HashSet;
import java.util.Set;

final class LinerAnalysisPolicy {

    private static final Set<QualitativeAnalysis.SpeakerPatternCategory> DAILY_PATTERNS = Set.of(
            QualitativeAnalysis.SpeakerPatternCategory.SHORT_RESPONSE,
            QualitativeAnalysis.SpeakerPatternCategory.REPEAT_OR_CONFIRM,
            QualitativeAnalysis.SpeakerPatternCategory.SITUATION_ACKNOWLEDGEMENT,
            QualitativeAnalysis.SpeakerPatternCategory.FOLLOW_UP_QUESTION,
            QualitativeAnalysis.SpeakerPatternCategory.EMOTION_QUESTION,
            QualitativeAnalysis.SpeakerPatternCategory.EXPRESSION_PATTERN
    );

    private static final Set<QualitativeAnalysis.SpeakerPatternCategory> CONFLICT_PATTERNS =
            conflictPatterns();

    private LinerAnalysisPolicy() {
    }

    static Set<QualitativeAnalysis.SpeakerPatternCategory> speakerPatternCategories(
            AnalysisScenario scenario
    ) {
        return switch (scenario) {
            case FRIEND_DAILY, COUPLE_DAILY -> DAILY_PATTERNS;
            case COUPLE_CONFLICT, PARENT_CHILD_CONFLICT -> CONFLICT_PATTERNS;
        };
    }

    static Set<QualitativeAnalysis.ScenarioInsightCategory> scenarioInsightCategories(
            AnalysisScenario scenario
    ) {
        return switch (scenario) {
            case FRIEND_DAILY -> Set.of(
                    QualitativeAnalysis.ScenarioInsightCategory.COMMON_INTEREST,
                    QualitativeAnalysis.ScenarioInsightCategory.PLAYFUL_EXCHANGE
            );
            case COUPLE_DAILY -> Set.of(
                    QualitativeAnalysis.ScenarioInsightCategory.COMMON_INTEREST,
                    QualitativeAnalysis.ScenarioInsightCategory.AFFECTION_EXPRESSION
            );
            case COUPLE_CONFLICT -> Set.of(
                    QualitativeAnalysis.ScenarioInsightCategory.CONFLICT_TOPIC,
                    QualitativeAnalysis.ScenarioInsightCategory.CONFLICT_POSITION,
                    QualitativeAnalysis.ScenarioInsightCategory.TURNING_POINT,
                    QualitativeAnalysis.ScenarioInsightCategory.MISSED_SIGNAL,
                    QualitativeAnalysis.ScenarioInsightCategory.SOLUTION
            );
            case PARENT_CHILD_CONFLICT -> Set.of(
                    QualitativeAnalysis.ScenarioInsightCategory.CONFLICT_TOPIC,
                    QualitativeAnalysis.ScenarioInsightCategory.CONFLICT_POSITION,
                    QualitativeAnalysis.ScenarioInsightCategory.TURNING_POINT,
                    QualitativeAnalysis.ScenarioInsightCategory.MISSED_SIGNAL,
                    QualitativeAnalysis.ScenarioInsightCategory.CONVERSATION_OPENNESS,
                    QualitativeAnalysis.ScenarioInsightCategory.CARE_EXPRESSION,
                    QualitativeAnalysis.ScenarioInsightCategory.QUESTION_RESPONSE_STYLE,
                    QualitativeAnalysis.ScenarioInsightCategory.SOLUTION
            );
        };
    }

    static Set<QualitativeAnalysis.ReportMetricType> reportMetricTypes(
            AnalysisScenario scenario
    ) {
        return switch (scenario) {
            case FRIEND_DAILY -> Set.of(
                    QualitativeAnalysis.ReportMetricType.COMPLIMENT,
                    QualitativeAnalysis.ReportMetricType.ENCOURAGEMENT,
                    QualitativeAnalysis.ReportMetricType.CARING_EXPRESSION,
                    QualitativeAnalysis.ReportMetricType.FRIENDLY_ADDRESS,
                    QualitativeAnalysis.ReportMetricType.DIRECT_AFFECTION,
                    QualitativeAnalysis.ReportMetricType.JOY_OR_GRATITUDE
            );
            case COUPLE_DAILY -> Set.of(
                    QualitativeAnalysis.ReportMetricType.COMPLIMENT,
                    QualitativeAnalysis.ReportMetricType.ENCOURAGEMENT,
                    QualitativeAnalysis.ReportMetricType.CARING_EXPRESSION,
                    QualitativeAnalysis.ReportMetricType.DIRECT_AFFECTION,
                    QualitativeAnalysis.ReportMetricType.JOY_OR_GRATITUDE,
                    QualitativeAnalysis.ReportMetricType.AFFECTIONATE_ADDRESS,
                    QualitativeAnalysis.ReportMetricType.COMPLIMENT_OR_ENCOURAGEMENT
            );
            case COUPLE_CONFLICT -> Set.of(
                    QualitativeAnalysis.ReportMetricType.POSITION_EXPLANATION,
                    QualitativeAnalysis.ReportMetricType.REBUTTAL,
                    QualitativeAnalysis.ReportMetricType.AVOIDANCE,
                    QualitativeAnalysis.ReportMetricType.APOLOGY,
                    QualitativeAnalysis.ReportMetricType.YIELDING,
                    QualitativeAnalysis.ReportMetricType.EMOTION_CHECK,
                    QualitativeAnalysis.ReportMetricType.DIRECT_EXPRESSION,
                    QualitativeAnalysis.ReportMetricType.ASSERTIVE_EXPRESSION,
                    QualitativeAnalysis.ReportMetricType.SOFTENING_EXPRESSION,
                    QualitativeAnalysis.ReportMetricType.RECOVERY_EXPRESSION,
                    QualitativeAnalysis.ReportMetricType.QUESTION,
                    QualitativeAnalysis.ReportMetricType.MISSED_RESPONSE
            );
            case PARENT_CHILD_CONFLICT -> Set.of(
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
                    QualitativeAnalysis.ReportMetricType.ADVICE,
                    QualitativeAnalysis.ReportMetricType.SOFTENING_EXPRESSION,
                    QualitativeAnalysis.ReportMetricType.CHILD_EXPRESSION,
                    QualitativeAnalysis.ReportMetricType.PARENT_CONFIRMATION
            );
        };
    }

    private static Set<QualitativeAnalysis.SpeakerPatternCategory> conflictPatterns() {
        Set<QualitativeAnalysis.SpeakerPatternCategory> categories = new HashSet<>(DAILY_PATTERNS);
        categories.addAll(Set.of(
                QualitativeAnalysis.SpeakerPatternCategory.ATTACK,
                QualitativeAnalysis.SpeakerPatternCategory.DEFENSE,
                QualitativeAnalysis.SpeakerPatternCategory.AVOIDANCE,
                QualitativeAnalysis.SpeakerPatternCategory.RECOVERY_ATTEMPT
        ));
        return Set.copyOf(categories);
    }
}
