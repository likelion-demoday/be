package com.example.resay.domain.analysis.model;

import java.util.List;

public record QualitativeAnalysis(
        Overview overview,
        List<TimelineItem> timeline,
        List<SpeakerInsight> speakerInsights,
        List<ScenarioInsight> scenarioInsights
) {

    public QualitativeAnalysis {
        timeline = copy(timeline);
        speakerInsights = copy(speakerInsights);
        scenarioInsights = copy(scenarioInsights);
    }

    public record Overview(
            String title,
            String description,
            List<Long> evidenceSegmentIds
    ) {

        public Overview {
            evidenceSegmentIds = copy(evidenceSegmentIds);
        }
    }

    public record TimelineItem(
            String title,
            String description,
            List<Long> evidenceSegmentIds
    ) {

        public TimelineItem {
            evidenceSegmentIds = copy(evidenceSegmentIds);
        }
    }

    public record SpeakerInsight(
            SpeakerRole speakerRole,
            List<SpeakerPattern> patterns
    ) {

        public SpeakerInsight {
            patterns = copy(patterns);
        }
    }

    public record SpeakerPattern(
            SpeakerPatternCategory category,
            String title,
            String description,
            List<Long> evidenceSegmentIds
    ) {

        public SpeakerPattern {
            evidenceSegmentIds = copy(evidenceSegmentIds);
        }
    }

    public record ScenarioInsight(
            ScenarioInsightCategory category,
            List<SpeakerRole> speakerRoles,
            String title,
            String description,
            List<Long> evidenceSegmentIds
    ) {

        public ScenarioInsight {
            speakerRoles = copy(speakerRoles);
            evidenceSegmentIds = copy(evidenceSegmentIds);
        }
    }

    public enum SpeakerPatternCategory {
        SHORT_RESPONSE,
        REPEAT_OR_CONFIRM,
        SITUATION_ACKNOWLEDGEMENT,
        FOLLOW_UP_QUESTION,
        EMOTION_QUESTION,
        EXPRESSION_PATTERN,
        ATTACK,
        DEFENSE,
        AVOIDANCE,
        RECOVERY_ATTEMPT
    }

    public enum ScenarioInsightCategory {
        COMMON_INTEREST,
        PLAYFUL_EXCHANGE,
        AFFECTION_EXPRESSION,
        CONFLICT_TOPIC,
        CONFLICT_POSITION,
        TURNING_POINT,
        MISSED_SIGNAL,
        CONVERSATION_OPENNESS,
        CARE_EXPRESSION,
        QUESTION_RESPONSE_STYLE,
        SOLUTION
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }
}
