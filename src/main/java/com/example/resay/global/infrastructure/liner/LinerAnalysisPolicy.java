package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import java.util.HashSet;
import java.util.Set;

final class LinerAnalysisPolicy {

    private static final Set<LinerAnalysisResponse.SpeakerPatternCategory> DAILY_PATTERNS = Set.of(
            LinerAnalysisResponse.SpeakerPatternCategory.SHORT_RESPONSE,
            LinerAnalysisResponse.SpeakerPatternCategory.REPEAT_OR_CONFIRM,
            LinerAnalysisResponse.SpeakerPatternCategory.SITUATION_ACKNOWLEDGEMENT,
            LinerAnalysisResponse.SpeakerPatternCategory.FOLLOW_UP_QUESTION,
            LinerAnalysisResponse.SpeakerPatternCategory.EMOTION_QUESTION,
            LinerAnalysisResponse.SpeakerPatternCategory.EXPRESSION_PATTERN
    );

    private static final Set<LinerAnalysisResponse.SpeakerPatternCategory> CONFLICT_PATTERNS =
            conflictPatterns();

    private LinerAnalysisPolicy() {
    }

    static Set<LinerAnalysisResponse.SpeakerPatternCategory> speakerPatternCategories(
            AnalysisScenario scenario
    ) {
        return switch (scenario) {
            case FRIEND_DAILY, COUPLE_DAILY -> DAILY_PATTERNS;
            case COUPLE_CONFLICT, PARENT_CHILD_CONFLICT -> CONFLICT_PATTERNS;
        };
    }

    static Set<LinerAnalysisResponse.ScenarioInsightCategory> scenarioInsightCategories(
            AnalysisScenario scenario
    ) {
        return switch (scenario) {
            case FRIEND_DAILY -> Set.of(
                    LinerAnalysisResponse.ScenarioInsightCategory.COMMON_INTEREST,
                    LinerAnalysisResponse.ScenarioInsightCategory.PLAYFUL_EXCHANGE
            );
            case COUPLE_DAILY -> Set.of(
                    LinerAnalysisResponse.ScenarioInsightCategory.COMMON_INTEREST,
                    LinerAnalysisResponse.ScenarioInsightCategory.AFFECTION_EXPRESSION
            );
            case COUPLE_CONFLICT -> Set.of(
                    LinerAnalysisResponse.ScenarioInsightCategory.CONFLICT_TOPIC,
                    LinerAnalysisResponse.ScenarioInsightCategory.CONFLICT_POSITION,
                    LinerAnalysisResponse.ScenarioInsightCategory.TURNING_POINT,
                    LinerAnalysisResponse.ScenarioInsightCategory.MISSED_SIGNAL,
                    LinerAnalysisResponse.ScenarioInsightCategory.SOLUTION
            );
            case PARENT_CHILD_CONFLICT -> Set.of(
                    LinerAnalysisResponse.ScenarioInsightCategory.CONFLICT_TOPIC,
                    LinerAnalysisResponse.ScenarioInsightCategory.CONFLICT_POSITION,
                    LinerAnalysisResponse.ScenarioInsightCategory.TURNING_POINT,
                    LinerAnalysisResponse.ScenarioInsightCategory.MISSED_SIGNAL,
                    LinerAnalysisResponse.ScenarioInsightCategory.CONVERSATION_OPENNESS,
                    LinerAnalysisResponse.ScenarioInsightCategory.CARE_EXPRESSION,
                    LinerAnalysisResponse.ScenarioInsightCategory.QUESTION_RESPONSE_STYLE,
                    LinerAnalysisResponse.ScenarioInsightCategory.SOLUTION
            );
        };
    }

    private static Set<LinerAnalysisResponse.SpeakerPatternCategory> conflictPatterns() {
        Set<LinerAnalysisResponse.SpeakerPatternCategory> categories = new HashSet<>(DAILY_PATTERNS);
        categories.addAll(Set.of(
                LinerAnalysisResponse.SpeakerPatternCategory.ATTACK,
                LinerAnalysisResponse.SpeakerPatternCategory.DEFENSE,
                LinerAnalysisResponse.SpeakerPatternCategory.AVOIDANCE,
                LinerAnalysisResponse.SpeakerPatternCategory.RECOVERY_ATTEMPT
        ));
        return Set.copyOf(categories);
    }
}
