package com.example.resay.domain.analysis.dto;

import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "근거 발화 정보가 제거된 공개 분석 보고서")
public record AnalysisReportDto(
        RecordingInfo recordingInfo,
        QuantitativeAnalysis quantitativeAnalysis,
        QualitativeReport qualitativeAnalysis
) {

    public static AnalysisReportDto from(AnalysisReport report) {
        return new AnalysisReportDto(
                RecordingInfo.from(report.recordingInfo()),
                QuantitativeAnalysis.from(report.quantitativeAnalysis()),
                QualitativeReport.from(report.qualitativeAnalysis())
        );
    }

    public record RecordingInfo(
            Long recordingId,
            AnalysisScenario scenario,
            Long durationMs,
            List<SpeakerInfo> speakers
    ) {

        private static RecordingInfo from(AnalysisReport.RecordingInfo source) {
            return new RecordingInfo(
                    source.recordingId(),
                    source.scenario(),
                    source.durationMs(),
                    source.speakers().stream().map(SpeakerInfo::from).toList()
            );
        }
    }

    public record SpeakerInfo(
            @Schema(description = "분석에서 사용하는 화자 역할", example = "SELF")
            SpeakerRole speakerRole,
            @Schema(description = "사용자가 입력한 화자 이름", example = "호준")
            String speakerName
    ) {

        private static SpeakerInfo from(
                com.example.resay.domain.analysis.model.AnalysisSpeaker source
        ) {
            return new SpeakerInfo(source.speakerRole(), source.speakerName());
        }
    }

    public record QuantitativeAnalysis(
            List<SpeakerMetrics> speakers,
            SpeakingSpeedComparison speakingSpeedComparison
    ) {

        private static QuantitativeAnalysis from(
                AnalysisReport.QuantitativeAnalysis source
        ) {
            return new QuantitativeAnalysis(
                    source.speakers().stream().map(SpeakerMetrics::from).toList(),
                    SpeakingSpeedComparison.from(source.speakingSpeedComparison())
            );
        }
    }

    public record SpeakerMetrics(
            SpeakerRole speakerRole,
            long speakingDurationMs,
            int utteranceCount,
            long transcribedCharacterCount,
            BigDecimal speakingRatioPercent,
            BigDecimal averageUtteranceDurationMs,
            BigDecimal charactersPerMinute
    ) {

        private static SpeakerMetrics from(
                com.example.resay.domain.analysis.model.SpeakerMetrics source
        ) {
            return new SpeakerMetrics(
                    source.speakerRole(),
                    source.speakingDurationMs(),
                    source.utteranceCount(),
                    source.transcribedCharacterCount(),
                    source.speakingRatioPercent(),
                    source.averageUtteranceDurationMs(),
                    source.charactersPerMinute()
            );
        }
    }

    public record SpeakingSpeedComparison(
            SpeakerRole fasterSpeakerRole,
            SpeakerRole slowerSpeakerRole,
            BigDecimal percentDifference
    ) {

        private static SpeakingSpeedComparison from(
                com.example.resay.domain.analysis.model.SpeakingSpeedComparison source
        ) {
            if (source == null) {
                return null;
            }
            return new SpeakingSpeedComparison(
                    source.fasterSpeakerRole(),
                    source.slowerSpeakerRole(),
                    source.percentDifference()
            );
        }
    }

    public record QualitativeReport(
            Overview overview,
            List<TimelineItem> timeline,
            List<TopicItem> topics,
            List<CharacterInsight> characterInsights,
            List<SpeakerInsight> speakerInsights,
            List<InterestInsight> interestInsights,
            List<SpicinessInsight> spicinessInsights,
            List<ReactionStyleInsight> reactionStyleInsights,
            List<ScenarioInsight> scenarioInsights
    ) {

        private static QualitativeReport from(AnalysisReport.QualitativeReport source) {
            return new QualitativeReport(
                    Overview.from(source.overview()),
                    source.timeline().stream().map(TimelineItem::from).toList(),
                    source.topics().stream().map(TopicItem::from).toList(),
                    source.characterInsights().stream().map(CharacterInsight::from).toList(),
                    source.speakerInsights().stream().map(SpeakerInsight::from).toList(),
                    source.interestInsights().stream().map(InterestInsight::from).toList(),
                    source.spicinessInsights().stream().map(SpicinessInsight::from).toList(),
                    source.reactionStyleInsights().stream()
                            .map(ReactionStyleInsight::from)
                            .toList(),
                    source.scenarioInsights().stream().map(ScenarioInsight::from).toList()
            );
        }
    }

    public record Overview(
            String title,
            String description
    ) {

        private static Overview from(QualitativeAnalysis.Overview source) {
            return new Overview(source.title(), source.description());
        }
    }

    public record TimelineItem(
            String title,
            String description,
            Long startMs,
            Long endMs
    ) {

        private static TimelineItem from(AnalysisReport.TimelineItem source) {
            return new TimelineItem(
                    source.title(),
                    source.description(),
                    source.startMs(),
                    source.endMs()
            );
        }
    }

    public record TopicItem(
            String title,
            String description,
            List<TopicTimeRange> timeRanges,
            Integer turnCount,
            Long speakingDurationMs,
            boolean longest
    ) {

        private static TopicItem from(AnalysisReport.TopicItem source) {
            return new TopicItem(
                    source.title(),
                    source.description(),
                    source.timeRanges().stream().map(TopicTimeRange::from).toList(),
                    source.turnCount(),
                    source.speakingDurationMs(),
                    source.longest()
            );
        }
    }

    public record TopicTimeRange(
            Long startMs,
            Long endMs
    ) {

        private static TopicTimeRange from(AnalysisReport.TopicTimeRange source) {
            return new TopicTimeRange(source.startMs(), source.endMs());
        }
    }

    public record CharacterInsight(
            SpeakerRole speakerRole,
            String name,
            String description
    ) {

        private static CharacterInsight from(QualitativeAnalysis.CharacterInsight source) {
            return new CharacterInsight(
                    source.speakerRole(),
                    source.name(),
                    source.description()
            );
        }
    }

    public record SpeakerInsight(
            SpeakerRole speakerRole,
            List<SpeakerPattern> patterns,
            FrequentExpressionSummary frequentExpressionSummary,
            List<FrequentExpression> frequentExpressions
    ) {

        private static SpeakerInsight from(QualitativeAnalysis.SpeakerInsight source) {
            return new SpeakerInsight(
                    source.speakerRole(),
                    source.patterns().stream().map(SpeakerPattern::from).toList(),
                    FrequentExpressionSummary.from(source.frequentExpressionSummary()),
                    source.frequentExpressions().stream().map(FrequentExpression::from).toList()
            );
        }
    }

    public record SpeakerPattern(
            QualitativeAnalysis.SpeakerPatternCategory category,
            String title,
            String description,
            int count
    ) {

        private static SpeakerPattern from(QualitativeAnalysis.SpeakerPattern source) {
            return new SpeakerPattern(
                    source.category(),
                    source.title(),
                    source.description(),
                    (int) source.evidenceSegmentIds().stream().distinct().count()
            );
        }
    }

    public record FrequentExpressionSummary(
            String title,
            String description
    ) {

        private static FrequentExpressionSummary from(
                QualitativeAnalysis.FrequentExpressionSummary source
        ) {
            if (source == null) {
                return null;
            }
            return new FrequentExpressionSummary(source.title(), source.description());
        }
    }

    public record FrequentExpression(
            QualitativeAnalysis.FrequentExpressionCategory category,
            String expression,
            int count
    ) {

        private static FrequentExpression from(QualitativeAnalysis.FrequentExpression source) {
            return new FrequentExpression(
                    source.category(),
                    source.expression(),
                    source.count()
            );
        }
    }

    public record InterestInsight(
            SpeakerRole speakerRole,
            int score,
            String description,
            List<InterestObservation> observations
    ) {

        private static InterestInsight from(QualitativeAnalysis.InterestInsight source) {
            return new InterestInsight(
                    source.speakerRole(),
                    source.score(),
                    source.description(),
                    source.observations().stream().map(InterestObservation::from).toList()
            );
        }
    }

    public record InterestObservation(
            QualitativeAnalysis.InterestCategory category,
            String title,
            String description
    ) {

        private static InterestObservation from(
                QualitativeAnalysis.InterestObservation source
        ) {
            return new InterestObservation(
                    source.category(),
                    source.title(),
                    source.description()
            );
        }
    }

    public record SpicinessInsight(
            SpeakerRole speakerRole,
            int score,
            @Schema(description = "전사문에서 확인된 비속어 총 등장 횟수", example = "3")
            int swearWordCount,
            String description,
            List<SpicinessObservation> observations
    ) {

        private static SpicinessInsight from(QualitativeAnalysis.SpicinessInsight source) {
            return new SpicinessInsight(
                    source.speakerRole(),
                    source.score(),
                    source.swearWords() == null ? 0 : source.swearWords().stream()
                            .mapToInt(QualitativeAnalysis.SwearWordUsage::count)
                            .sum(),
                    source.description(),
                    source.observations().stream().map(SpicinessObservation::from).toList()
            );
        }
    }

    public record SpicinessObservation(
            QualitativeAnalysis.SpicinessCategory category,
            String title,
            String description
    ) {

        private static SpicinessObservation from(
                QualitativeAnalysis.SpicinessObservation source
        ) {
            return new SpicinessObservation(
                    source.category(),
                    source.title(),
                    source.description()
            );
        }
    }

    public record ReactionStyleInsight(
            SpeakerRole speakerRole,
            int thinkingPercent,
            int feelingPercent,
            String description,
            List<ReactionExample> examples
    ) {

        private static ReactionStyleInsight from(
                QualitativeAnalysis.ReactionStyleInsight source
        ) {
            return new ReactionStyleInsight(
                    source.speakerRole(),
                    source.thinkingPercent(),
                    source.feelingPercent(),
                    source.description(),
                    source.examples().stream().map(ReactionExample::from).toList()
            );
        }
    }

    public record ReactionExample(
            QualitativeAnalysis.ReactionCategory category,
            String title,
            String description
    ) {

        private static ReactionExample from(QualitativeAnalysis.ReactionExample source) {
            return new ReactionExample(
                    source.category(),
                    source.title(),
                    source.description()
            );
        }
    }

    public record ScenarioInsight(
            QualitativeAnalysis.ScenarioInsightCategory category,
            List<SpeakerRole> speakerRoles,
            String title,
            String description
    ) {

        private static ScenarioInsight from(QualitativeAnalysis.ScenarioInsight source) {
            return new ScenarioInsight(
                    source.category(),
                    source.speakerRoles(),
                    source.title(),
                    source.description()
            );
        }
    }
}
