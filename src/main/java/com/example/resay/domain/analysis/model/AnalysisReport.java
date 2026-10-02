package com.example.resay.domain.analysis.model;

import java.util.List;

public record AnalysisReport(
        RecordingInfo recordingInfo,
        QuantitativeAnalysis quantitativeAnalysis,
        QualitativeAnalysis qualitativeAnalysis
) {

    public record RecordingInfo(
            Long recordingId,
            AnalysisScenario scenario,
            Long durationMs
    ) {
    }

    public record QuantitativeAnalysis(
            List<SpeakerMetrics> speakers,
            SpeakingSpeedComparison speakingSpeedComparison
    ) {

        public QuantitativeAnalysis {
            speakers = List.copyOf(speakers);
        }
    }

    public record QualitativeAnalysis(
            Overview overview,
            List<TimelineItem> timeline,
            List<SpeakerInsight> speakerInsights,
            List<ScenarioInsight> scenarioInsights
    ) {

        public QualitativeAnalysis {
            timeline = List.copyOf(timeline);
            speakerInsights = List.copyOf(speakerInsights);
            scenarioInsights = List.copyOf(scenarioInsights);
        }
    }

    public record Overview(
            String title,
            String description,
            List<Long> evidenceSegmentIds
    ) {

        public Overview {
            evidenceSegmentIds = List.copyOf(evidenceSegmentIds);
        }
    }

    public record TimelineItem(
            String title,
            String description,
            List<Long> evidenceSegmentIds,
            Long startMs,
            Long endMs
    ) {

        public TimelineItem {
            evidenceSegmentIds = List.copyOf(evidenceSegmentIds);
        }
    }

    public record SpeakerInsight(
            SpeakerRole speakerRole,
            List<SpeakerPattern> patterns
    ) {

        public SpeakerInsight {
            patterns = List.copyOf(patterns);
        }
    }

    public record SpeakerPattern(
            SpeakerPatternCategory category,
            String title,
            String description,
            List<Long> evidenceSegmentIds
    ) {

        public SpeakerPattern {
            evidenceSegmentIds = List.copyOf(evidenceSegmentIds);
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
            speakerRoles = List.copyOf(speakerRoles);
            evidenceSegmentIds = List.copyOf(evidenceSegmentIds);
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
}
