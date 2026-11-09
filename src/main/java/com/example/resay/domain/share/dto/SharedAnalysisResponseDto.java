package com.example.resay.domain.share.dto;

import com.example.resay.domain.analysis.dto.AnalysisReportDto;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(
        description = "공유 페이지 전용 분석 보고서. 사용자 이름, 내부 식별자, 원본 발화와 모델 메타데이터는 포함하지 않습니다."
)
public record SharedAnalysisResponseDto(
        AnalysisScenario scenario,
        @Schema(description = "녹음 전체 길이(밀리초)")
        Long durationMs,
        @Schema(description = "녹음 전체 길이(초)")
        Long durationSeconds,
        LocalDateTime recordedAt,
        @Schema(description = "이름을 제외한 보고서 화자 역할")
        List<SpeakerRole> speakerRoles,
        AnalysisReportDto.QuantitativeAnalysis quantitativeAnalysis,
        SharedQualitativeReport qualitativeAnalysis
) {

    public static SharedAnalysisResponseDto from(AnalysisReport report) {
        AnalysisReportDto source = AnalysisReportDto.from(report);
        AnalysisReportDto.RecordingInfo recordingInfo = source.recordingInfo();
        return new SharedAnalysisResponseDto(
                recordingInfo.scenario(),
                recordingInfo.durationMs(),
                recordingInfo.durationSeconds(),
                recordingInfo.recordedAt(),
                recordingInfo.speakers().stream()
                        .map(AnalysisReportDto.SpeakerInfo::speakerRole)
                        .toList(),
                source.quantitativeAnalysis(),
                SharedQualitativeReport.from(source.qualitativeAnalysis())
        );
    }

    public record SharedQualitativeReport(
            AnalysisReportDto.Overview overview,
            List<AnalysisReportDto.TimelineItem> timeline,
            List<AnalysisReportDto.TopicItem> topics,
            List<AnalysisReportDto.CharacterInsight> characterInsights,
            List<SharedSpeakerInsight> speakerInsights,
            List<AnalysisReportDto.InterestInsight> interestInsights,
            List<AnalysisReportDto.SpicinessInsight> spicinessInsights,
            List<AnalysisReportDto.ReactionStyleInsight> reactionStyleInsights,
            List<AnalysisReportDto.ScenarioInsight> scenarioInsights,
            List<SharedRepresentativeEvidence> representativeEvidences,
            AnalysisReportDto.RelationshipSummary relationshipSummary,
            List<AnalysisReportDto.SpeakerAffectionSummary> speakerAffectionSummaries,
            List<SharedReportMetric> reportMetrics,
            List<AnalysisReportDto.SpeakerTendency> speakerTendencies,
            List<SharedCoachingSuggestion> coachingSuggestions
    ) {

        private static SharedQualitativeReport from(
                AnalysisReportDto.QualitativeReport source
        ) {
            return new SharedQualitativeReport(
                    source.overview(),
                    source.timeline(),
                    source.topics(),
                    source.characterInsights(),
                    source.speakerInsights().stream().map(SharedSpeakerInsight::from).toList(),
                    source.interestInsights(),
                    source.spicinessInsights(),
                    source.reactionStyleInsights(),
                    source.scenarioInsights(),
                    source.representativeEvidences().stream()
                            .map(SharedRepresentativeEvidence::from)
                            .toList(),
                    source.relationshipSummary(),
                    source.speakerAffectionSummaries(),
                    source.reportMetrics().stream().map(SharedReportMetric::from).toList(),
                    source.speakerTendencies(),
                    source.coachingSuggestions().stream()
                            .map(SharedCoachingSuggestion::from)
                            .toList()
            );
        }
    }

    public record SharedSpeakerInsight(
            SpeakerRole speakerRole,
            List<SharedSpeakerPattern> patterns,
            AnalysisReportDto.FrequentExpressionSummary frequentExpressionSummary,
            List<AnalysisReportDto.FrequentExpression> frequentExpressions
    ) {

        private static SharedSpeakerInsight from(AnalysisReportDto.SpeakerInsight source) {
            return new SharedSpeakerInsight(
                    source.speakerRole(),
                    source.patterns().stream().map(SharedSpeakerPattern::from).toList(),
                    source.frequentExpressionSummary(),
                    source.frequentExpressions()
            );
        }
    }

    public record SharedSpeakerPattern(
            QualitativeAnalysis.SpeakerPatternCategory category,
            String title,
            String description,
            int count,
            List<SharedEvidenceQuote> evidenceQuotes
    ) {

        private static SharedSpeakerPattern from(AnalysisReportDto.SpeakerPattern source) {
            return new SharedSpeakerPattern(
                    source.category(),
                    source.title(),
                    source.description(),
                    source.count(),
                    source.evidenceQuotes().stream().map(SharedEvidenceQuote::from).toList()
            );
        }
    }

    public record SharedRepresentativeEvidence(
            QualitativeAnalysis.ReportEvidenceType type,
            SpeakerRole speakerRole,
            QualitativeAnalysis.ReactionCategory reactionCategory,
            SharedEvidenceQuote evidence
    ) {

        private static SharedRepresentativeEvidence from(
                AnalysisReportDto.RepresentativeEvidence source
        ) {
            return new SharedRepresentativeEvidence(
                    source.type(),
                    source.speakerRole(),
                    source.reactionCategory(),
                    SharedEvidenceQuote.from(source.evidence())
            );
        }
    }

    public record SharedReportMetric(
            QualitativeAnalysis.ReportMetricType type,
            SpeakerRole speakerRole,
            int value,
            QualitativeAnalysis.ReportMetricUnit unit,
            String title,
            String description,
            List<SharedEvidenceQuote> evidenceQuotes
    ) {

        private static SharedReportMetric from(AnalysisReportDto.ReportMetric source) {
            return new SharedReportMetric(
                    source.type(),
                    source.speakerRole(),
                    source.value(),
                    source.unit(),
                    source.title(),
                    source.description(),
                    source.evidenceQuotes().stream().map(SharedEvidenceQuote::from).toList()
            );
        }
    }

    public record SharedCoachingSuggestion(
            SpeakerRole speakerRole,
            int order,
            String title,
            String description,
            String suggestedPhrase,
            List<SharedEvidenceQuote> evidenceQuotes
    ) {

        private static SharedCoachingSuggestion from(
                AnalysisReportDto.CoachingSuggestion source
        ) {
            return new SharedCoachingSuggestion(
                    source.speakerRole(),
                    source.order(),
                    source.title(),
                    source.description(),
                    source.suggestedPhrase(),
                    source.evidenceQuotes().stream().map(SharedEvidenceQuote::from).toList()
            );
        }
    }

    public record SharedEvidenceQuote(
            SpeakerRole speakerRole,
            @Schema(description = "화면 표시용으로 최소 보정된 발화. 원본 발화는 제공하지 않습니다.")
            String displayContent,
            Long startMs,
            Long endMs
    ) {

        private static SharedEvidenceQuote from(AnalysisReportDto.EvidenceQuote source) {
            return new SharedEvidenceQuote(
                    source.speakerRole(),
                    source.displayContent(),
                    source.startMs(),
                    source.endMs()
            );
        }
    }
}
