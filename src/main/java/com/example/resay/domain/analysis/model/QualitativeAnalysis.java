package com.example.resay.domain.analysis.model;

import java.util.List;

public record QualitativeAnalysis(
        Overview overview,
        List<TimelineItem> timeline,
        List<Topic> topics, // 주제와 해당 발화 전체
        List<CharacterInsight> characterInsights, // 화자별 캐릭터
        List<SpeakerInsight> speakerInsights,
        List<InterestInsight> interestInsights, // 관심도 점수와 관찰 근거
        List<SpicinessInsight> spicinessInsights, // 표독력 점수와 강한 표현의 맥락
        List<ReactionStyleInsight> reactionStyleInsights, // T/F 비율과 대표 반응
        List<ScenarioInsight> scenarioInsights
) {

    public QualitativeAnalysis {
        timeline = copy(timeline);
        topics = copy(topics);
        characterInsights = copy(characterInsights);
        speakerInsights = copy(speakerInsights);
        interestInsights = copy(interestInsights);
        spicinessInsights = copy(spicinessInsights);
        reactionStyleInsights = copy(reactionStyleInsights);
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

    public record Topic(
            String title,
            String description,
            List<Long> segmentIds
    ) {
        public Topic {
            segmentIds = copy(segmentIds);
        }
    }

    public record CharacterInsight(
            SpeakerRole speakerRole,
            String name,
            String description,
            List<Long> evidenceSegmentIds
    ) {
        public CharacterInsight {
            evidenceSegmentIds = copy(evidenceSegmentIds);
        }
    }

    public record SpeakerInsight(
            SpeakerRole speakerRole,
            List<SpeakerPattern> patterns,
            SentenceStyle sentenceStyle,
            List<FrequentExpression> frequentExpressions
    ) {
        public SpeakerInsight {
            patterns = copy(patterns);
            frequentExpressions = copy(frequentExpressions);
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

    public record SentenceStyle(
            String title,
            String description,
            List<Long> evidenceSegmentIds
    ) {
        public SentenceStyle {
            evidenceSegmentIds = copy(evidenceSegmentIds);
        }
    }

    public record FrequentExpression(
            String expression,
            int count,
            String description,
            List<Long> evidenceSegmentIds
    ) {
        public FrequentExpression {
            evidenceSegmentIds = copy(evidenceSegmentIds);
        }
    }

    public record InterestInsight(
            SpeakerRole speakerRole,
            int score,
            String description,
            List<InterestObservation> observations
    ) {
        public InterestInsight {
            observations = copy(observations);
        }
    }

    public record InterestObservation(
            InterestCategory category,
            String title,
            String description,
            List<Long> evidenceSegmentIds
    ) {
        public InterestObservation {
            evidenceSegmentIds = copy(evidenceSegmentIds);
        }
    }

    public record SpicinessInsight(
            SpeakerRole speakerRole,
            int score,
            String description,
            List<SpicinessObservation> observations
    ) {
        public SpicinessInsight {
            observations = copy(observations);
        }
    }

    public record SpicinessObservation(
            SpicinessCategory category,
            String title,
            String description,
            List<Long> evidenceSegmentIds
    ) {
        public SpicinessObservation {
            evidenceSegmentIds = copy(evidenceSegmentIds);
        }
    }

    public record ReactionStyleInsight(
            SpeakerRole speakerRole,
            int thinkingPercent,
            int feelingPercent,
            String description,
            List<ReactionExample> examples
    ) {
        public ReactionStyleInsight {
            examples = copy(examples);
        }
    }

    public record ReactionExample(
            ReactionCategory category,
            String title,
            String description,
            List<Long> evidenceSegmentIds
    ) {
        public ReactionExample {
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

    public enum InterestCategory {
        BACKCHANNEL,
        EMPATHY,
        QUESTION,
        AFFECTION_EXPRESSION
    }

    public enum SpicinessCategory {
        SWEAR_WORD,
        DIRECTNESS,
        EXPRESSION_INTENSITY,
        SOFTENING_EXPRESSION,
        ATTACK_TARGET,
        PLAYFUL_CONTEXT,
        RECOVERY_EXPRESSION
    }

    public enum ReactionCategory {
        THINKING,
        FEELING,
        MIXED
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
