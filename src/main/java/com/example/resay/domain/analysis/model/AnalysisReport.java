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

    public record QualitativeReport(
            QualitativeAnalysis.Overview overview,
            List<TimelineItem> timeline,
            List<QualitativeAnalysis.SpeakerInsight> speakerInsights,
            List<QualitativeAnalysis.ScenarioInsight> scenarioInsights
    ) {

        public QualitativeReport {
            timeline = List.copyOf(timeline);
            speakerInsights = List.copyOf(speakerInsights);
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

}
