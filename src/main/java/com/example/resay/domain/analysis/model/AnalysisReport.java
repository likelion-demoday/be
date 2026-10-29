package com.example.resay.domain.analysis.model;

import java.time.LocalDateTime;
import java.util.List;

public record AnalysisReport(
        RecordingInfo recordingInfo,
        QuantitativeAnalysis quantitativeAnalysis,
        QualitativeReport qualitativeAnalysis
) {

    public record RecordingInfo(
            Long recordingId,
            AnalysisScenario scenario,
            Long durationMs,
            LocalDateTime recordedAt,
            List<AnalysisSpeaker> speakers
    ) {

        public RecordingInfo(
                Long recordingId,
                AnalysisScenario scenario,
                Long durationMs,
                List<AnalysisSpeaker> speakers
        ) {
            this(recordingId, scenario, durationMs, null, speakers);
        }

        public RecordingInfo {
            speakers = speakers == null ? List.of() : List.copyOf(speakers);
        }
    }

    public record QuantitativeAnalysis(
            List<SpeakerMetrics> speakers,
            SpeakingSpeedComparison speakingSpeedComparison,
            Integer totalTurnCount
    ) {

        public QuantitativeAnalysis(
                List<SpeakerMetrics> speakers,
                SpeakingSpeedComparison speakingSpeedComparison
        ) {
            this(speakers, speakingSpeedComparison, 0);
        }

        public QuantitativeAnalysis {
            speakers = List.copyOf(speakers);
            totalTurnCount = totalTurnCount == null ? 0 : totalTurnCount;
        }
    }

    public record QualitativeReport(
            QualitativeAnalysis.Overview overview,
            List<TimelineItem> timeline,
            List<TopicItem> topics,
            List<QualitativeAnalysis.CharacterInsight> characterInsights,
            List<QualitativeAnalysis.SpeakerInsight> speakerInsights,
            List<QualitativeAnalysis.InterestInsight> interestInsights,
            List<QualitativeAnalysis.SpicinessInsight> spicinessInsights,
            List<QualitativeAnalysis.ReactionStyleInsight> reactionStyleInsights,
            List<QualitativeAnalysis.ScenarioInsight> scenarioInsights,
            List<RepresentativeEvidence> representativeEvidences,
            QualitativeAnalysis.RelationshipSummary relationshipSummary,
            List<QualitativeAnalysis.ReportMetric> reportMetrics,
            List<QualitativeAnalysis.SpeakerTendency> speakerTendencies,
            List<QualitativeAnalysis.CoachingSuggestion> coachingSuggestions
    ) {

        public QualitativeReport {
            timeline = List.copyOf(timeline);
            topics = List.copyOf(topics);
            characterInsights = List.copyOf(characterInsights);
            speakerInsights = List.copyOf(speakerInsights);
            interestInsights = List.copyOf(interestInsights);
            spicinessInsights = List.copyOf(spicinessInsights);
            reactionStyleInsights = List.copyOf(reactionStyleInsights);
            scenarioInsights = List.copyOf(scenarioInsights);
            representativeEvidences = representativeEvidences == null
                    ? List.of()
                    : List.copyOf(representativeEvidences);
            reportMetrics = reportMetrics == null ? List.of() : List.copyOf(reportMetrics);
            speakerTendencies = speakerTendencies == null
                    ? List.of()
                    : List.copyOf(speakerTendencies);
            coachingSuggestions = coachingSuggestions == null
                    ? List.of()
                    : List.copyOf(coachingSuggestions);
        }

        public QualitativeReport(
                QualitativeAnalysis.Overview overview,
                List<TimelineItem> timeline,
                List<TopicItem> topics,
                List<QualitativeAnalysis.CharacterInsight> characterInsights,
                List<QualitativeAnalysis.SpeakerInsight> speakerInsights,
                List<QualitativeAnalysis.InterestInsight> interestInsights,
                List<QualitativeAnalysis.SpicinessInsight> spicinessInsights,
                List<QualitativeAnalysis.ReactionStyleInsight> reactionStyleInsights,
                List<QualitativeAnalysis.ScenarioInsight> scenarioInsights
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
                    List.of(),
                    null,
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        public QualitativeReport(
                QualitativeAnalysis.Overview overview,
                List<TimelineItem> timeline,
                List<TopicItem> topics,
                List<QualitativeAnalysis.CharacterInsight> characterInsights,
                List<QualitativeAnalysis.SpeakerInsight> speakerInsights,
                List<QualitativeAnalysis.InterestInsight> interestInsights,
                List<QualitativeAnalysis.SpicinessInsight> spicinessInsights,
                List<QualitativeAnalysis.ReactionStyleInsight> reactionStyleInsights,
                List<QualitativeAnalysis.ScenarioInsight> scenarioInsights,
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
    }

    public record RepresentativeEvidence(
            QualitativeAnalysis.ReportEvidenceType type,
            SpeakerRole speakerRole,
            QualitativeAnalysis.ReactionCategory reactionCategory,
            EvidenceQuote evidence
    ) {
    }

    public record EvidenceQuote(
            Long segmentId,
            SpeakerRole speakerRole,
            String originalContent,
            String displayContent,
            boolean correctionApplied,
            Long startMs,
            Long endMs
    ) {
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

    public record TopicItem(
            String title,
            String description,
            List<Long> segmentIds,
            List<TopicTimeRange> timeRanges,
            Integer turnCount,
            Long speakingDurationMs,
            boolean longest
    ) {

        public TopicItem {
            segmentIds = List.copyOf(segmentIds);
            timeRanges = List.copyOf(timeRanges);
        }
    }

    public record TopicTimeRange(
            Long startMs,
            Long endMs
    ) {
    }

}
