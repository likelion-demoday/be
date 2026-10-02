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
