package com.example.resay.domain.analysis.model;

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
            List<AnalysisSpeaker> speakers
    ) {

        public RecordingInfo {
            speakers = speakers == null ? List.of() : List.copyOf(speakers);
        }
    }

    public record QuantitativeAnalysis(
            List<SpeakerMetrics> speakers,
            SpeakingSpeedComparison speakingSpeedComparison
    ) {

        public QuantitativeAnalysis {
            speakers = List.copyOf(speakers);
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
            List<QualitativeAnalysis.ScenarioInsight> scenarioInsights
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
