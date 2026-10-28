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
        List<ScenarioInsight> scenarioInsights,
        List<RepresentativeEvidence> representativeEvidences, // Figma에 노출할 대표 발화
        RelationshipSummary relationshipSummary,
        List<ReportMetric> reportMetrics,
        List<SpeakerTendency> speakerTendencies,
        List<CoachingSuggestion> coachingSuggestions
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
        representativeEvidences = copy(representativeEvidences);
        reportMetrics = reportMetrics == null ? List.of() : List.copyOf(reportMetrics);
        speakerTendencies = speakerTendencies == null ? List.of() : List.copyOf(speakerTendencies);
        coachingSuggestions = coachingSuggestions == null
                ? List.of()
                : List.copyOf(coachingSuggestions);
    }

    public QualitativeAnalysis(
            Overview overview,
            List<TimelineItem> timeline,
            List<Topic> topics,
            List<CharacterInsight> characterInsights,
            List<SpeakerInsight> speakerInsights,
            List<InterestInsight> interestInsights,
            List<SpicinessInsight> spicinessInsights,
            List<ReactionStyleInsight> reactionStyleInsights,
            List<ScenarioInsight> scenarioInsights,
            List<RepresentativeEvidence> representativeEvidences
    ) {
        this(
                overview,
                timeline,
                topics,
                characterInsights,
                speakerInsights,
                interestInsights,
                spicinessInsights,
                reactionStyleInsights,
                scenarioInsights,
                representativeEvidences,
                null,
                List.of(),
                List.of(),
                List.of()
        );
    }

    public QualitativeAnalysis(
            Overview overview,
            List<TimelineItem> timeline,
            List<Topic> topics,
            List<CharacterInsight> characterInsights,
            List<SpeakerInsight> speakerInsights,
            List<InterestInsight> interestInsights,
            List<SpicinessInsight> spicinessInsights,
            List<ReactionStyleInsight> reactionStyleInsights,
            List<ScenarioInsight> scenarioInsights
    ) {
        this(
                overview,
                timeline,
                topics,
                characterInsights,
                speakerInsights,
                interestInsights,
                spicinessInsights,
                reactionStyleInsights,
                scenarioInsights,
                null,
                null,
                List.of(),
                List.of(),
                List.of()
        );
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
            FrequentExpressionSummary frequentExpressionSummary,
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

    public record FrequentExpressionSummary(
            String title,
            String description,
            List<Long> evidenceSegmentIds
    ) {
        public FrequentExpressionSummary {
            evidenceSegmentIds = copy(evidenceSegmentIds);
        }
    }

    public record FrequentExpression(
            FrequentExpressionCategory category,
            String expression,
            int count,
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
            List<SwearWordUsage> swearWords,
            List<SpicinessObservation> observations
    ) {
        public SpicinessInsight {
            swearWords = copy(swearWords);
            observations = copy(observations);
        }
    }

    public record SwearWordUsage(
            String expression,
            int count,
            List<Long> evidenceSegmentIds
    ) {
        public SwearWordUsage {
            evidenceSegmentIds = copy(evidenceSegmentIds);
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

    public record RepresentativeEvidence(
            ReportEvidenceType type,
            SpeakerRole speakerRole,
            ReactionCategory reactionCategory,
            Long segmentId,
            String displayContent
    ) {
    }

    public record RelationshipSummary(
            String title,
            String description,
            List<Long> evidenceSegmentIds
    ) {
        public RelationshipSummary {
            evidenceSegmentIds = copy(evidenceSegmentIds);
        }
    }

    public record ReportMetric(
            ReportMetricType type,
            SpeakerRole speakerRole,
            int value,
            ReportMetricUnit unit,
            String title,
            String description,
            List<Long> evidenceSegmentIds
    ) {
        public ReportMetric {
            evidenceSegmentIds = copy(evidenceSegmentIds);
        }
    }

    public record SpeakerTendency(
            SpeakerRole speakerRole,
            String label,
            String description,
            List<Long> evidenceSegmentIds
    ) {
        public SpeakerTendency {
            evidenceSegmentIds = copy(evidenceSegmentIds);
        }
    }

    public record CoachingSuggestion(
            int order,
            String title,
            String description,
            String suggestedPhrase,
            List<Long> evidenceSegmentIds
    ) {
        public CoachingSuggestion {
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

    public enum FrequentExpressionCategory {
        SPEECH_HABIT,
        EMPHASIS,
        WORD
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

    public enum ReportEvidenceType {
        REACTION_STYLE_EXAMPLE,
        EMOTION_EXPRESSION,
        MISSED_QUESTION,
        FOLLOWING_RESPONSE,
        SPEAKER_REACTION,
        RECOVERY_START,
        CHILD_POSITION,
        FOLLOWING_ADVICE,
        UNASKED_QUESTION,
        WORRY_START,
        EXPRESSION_TURNING_POINT,
        CLOSING_RESPONSE
    }

    public enum ReportMetricUnit {
        COUNT,
        PERCENT
    }

    public enum ReportMetricType {
        COMPLIMENT,
        ENCOURAGEMENT,
        CARING_EXPRESSION,
        FRIENDLY_ADDRESS,
        DIRECT_AFFECTION,
        JOY_OR_GRATITUDE,
        AFFECTIONATE_ADDRESS,
        COMPLIMENT_OR_ENCOURAGEMENT,
        POSITION_EXPLANATION,
        REBUTTAL,
        AVOIDANCE,
        APOLOGY,
        YIELDING,
        EMOTION_CHECK,
        DIRECT_EXPRESSION,
        ASSERTIVE_EXPRESSION,
        SOFTENING_EXPRESSION,
        RECOVERY_EXPRESSION,
        QUESTION,
        MISSED_RESPONSE,
        SITUATION_QUESTION,
        CONCRETE_ADVICE,
        EXPERIENCE_COMPARISON,
        SHORT_RESPONSE,
        TOPIC_AVOIDANCE,
        EMOTION_EXPRESSION,
        CHILD_EXPRESSION,
        PARENT_CONFIRMATION,
        ADVICE
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? null : List.copyOf(values);
    }
}
