package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.dto.AnalysisSummaryResponseDto;
import com.example.resay.domain.analysis.entity.AnalysisStatus;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSpeaker;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerMetrics;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.repository.AnalysisResultRepository;
import com.example.resay.domain.analysis.repository.AnalysisSummarySourceProjection;
import com.example.resay.domain.recording.entity.ParentChildRole;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisSummaryQueryServiceTest {

    @Mock
    private AnalysisResultRepository analysisResultRepository;

    private ObjectMapper objectMapper;
    private AnalysisSummaryQueryService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new AnalysisSummaryQueryService(analysisResultRepository, objectMapper);
    }

    @Test
    void aggregatesRecentSelfMetricsAndReturnsOldestHistoryFirst() throws Exception {
        LocalDateTime older = LocalDateTime.of(2026, 10, 1, 12, 0);
        LocalDateTime newer = LocalDateTime.of(2026, 10, 5, 12, 0);
        when(analysisResultRepository.findRecentSummarySources(
                eq(1L),
                eq(AnalysisStatus.COMPLETED),
                any()
        )).thenReturn(List.of(
                source(2L, newer, null, friendReport(
                        2L, 40, 100, 120_000L, 840, 1, "진짜", 3
                )),
                source(1L, older, null, friendReport(
                        1L, 60, 100, 60_000L, 300, 1, "진짜", 2
                ))
        ));

        AnalysisSummaryResponseDto result = service.getSummary(1L);

        assertThat(result.analysisCount()).isEqualTo(2);
        assertThat(result.frequentExpressions()).containsExactly(
                new AnalysisSummaryResponseDto.FrequentExpression("진짜", 5)
        );
        assertThat(result.swearWordUsage().count()).isEqualTo(2);
        assertThat(result.swearWordUsage().ratePercent()).isEqualByComparingTo("1.00");
        assertThat(result.speakingSpeed().syllablesPerSecond()).isEqualByComparingTo("6.33");
        assertThat(result.speakingSpeed().referenceSyllablesPerSecond())
                .isEqualByComparingTo("5.79");
        assertThat(result.speakingSpeed().differencePercent()).isEqualByComparingTo("9.33");
        assertThat(result.speakingSpeed().level())
                .isEqualTo(AnalysisSummaryResponseDto.SpeakingSpeedLevel.TYPICAL);
        assertThat(result.speakingSpeed().description())
                .isEqualTo("한국인 평균과 비슷한 속도로 말해요.");
        assertThat(result.speakingRatioHistory())
                .extracting(AnalysisSummaryResponseDto.SpeakingRatioHistory::recordingId)
                .containsExactly(1L, 2L);
    }

    @Test
    void usesSelectedParentRoleAsSelfInParentChildAnalysis() throws Exception {
        AnalysisReport report = report(
                3L,
                AnalysisScenario.PARENT_CHILD_CONFLICT,
                List.of(
                        metrics(SpeakerRole.PARENT, 70, 80, 60_000L, 390),
                        metrics(SpeakerRole.CHILD, 30, 120, 60_000L, 300)
                ),
                List.of(
                        speakerInsight(SpeakerRole.PARENT, "그러니까", 4),
                        speakerInsight(SpeakerRole.CHILD, "진짜", 9)
                ),
                List.of(
                        spiciness(SpeakerRole.PARENT, 2),
                        spiciness(SpeakerRole.CHILD, 7)
                )
        );
        when(analysisResultRepository.findRecentSummarySources(
                eq(1L),
                eq(AnalysisStatus.COMPLETED),
                any()
        )).thenReturn(List.of(source(
                3L,
                LocalDateTime.of(2026, 10, 6, 12, 0),
                ParentChildRole.PARENT,
                report
        )));

        AnalysisSummaryResponseDto result = service.getSummary(1L);

        assertThat(result.frequentExpressions()).containsExactly(
                new AnalysisSummaryResponseDto.FrequentExpression("그러니까", 4)
        );
        assertThat(result.swearWordUsage().count()).isEqualTo(2);
        assertThat(result.swearWordUsage().ratePercent()).isEqualByComparingTo("2.50");
        assertThat(result.speakingSpeed().syllablesPerSecond()).isEqualByComparingTo("6.50");
        assertThat(result.speakingSpeed().level())
                .isEqualTo(AnalysisSummaryResponseDto.SpeakingSpeedLevel.TYPICAL);
        assertThat(result.speakingRatioHistory().get(0).speakingRatioPercent())
                .isEqualByComparingTo("70");
    }

    @Test
    void returnsEmptySummaryWhenCompletedAnalysisDoesNotExist() {
        when(analysisResultRepository.findRecentSummarySources(
                eq(1L),
                eq(AnalysisStatus.COMPLETED),
                any()
        )).thenReturn(List.of());

        AnalysisSummaryResponseDto result = service.getSummary(1L);

        assertThat(result.analysisCount()).isZero();
        assertThat(result.frequentExpressions()).isEmpty();
        assertThat(result.swearWordUsage().count()).isZero();
        assertThat(result.swearWordUsage().ratePercent()).isEqualByComparingTo("0.00");
        assertThat(result.speakingSpeed()).isNull();
        assertThat(result.speakingRatioHistory()).isEmpty();
    }

    @Test
    void classifiesSpeakingSpeedWithResearchBasedServiceRanges() throws Exception {
        when(analysisResultRepository.findRecentSummarySources(
                eq(1L),
                eq(AnalysisStatus.COMPLETED),
                any()
        )).thenReturn(List.of(source(
                4L,
                LocalDateTime.of(2026, 10, 7, 12, 0),
                null,
                friendReport(4L, 50, 100, 60_000L, 420, 0, "진짜", 1)
        )));

        AnalysisSummaryResponseDto result = service.getSummary(1L);

        assertThat(result.speakingSpeed().syllablesPerSecond()).isEqualByComparingTo("7.00");
        assertThat(result.speakingSpeed().level())
                .isEqualTo(AnalysisSummaryResponseDto.SpeakingSpeedLevel.FAST);
        assertThat(result.speakingSpeed().description())
                .isEqualTo("한국인 평균보다 약 21% 빠르게 말해요.");
    }

    private AnalysisSummarySourceProjection source(
            Long recordingId,
            LocalDateTime analyzedAt,
            ParentChildRole parentChildRole,
            AnalysisReport report
    ) throws Exception {
        return new SummarySource(
                recordingId,
                analyzedAt,
                parentChildRole,
                objectMapper.writeValueAsString(report)
        );
    }

    private AnalysisReport friendReport(
            Long recordingId,
            int speakingRatio,
            long wordCount,
            long speakingDurationMs,
            long syllableCount,
            int swearWordCount,
            String expression,
            int expressionCount
    ) {
        return report(
                recordingId,
                AnalysisScenario.FRIEND_DAILY,
                List.of(metrics(
                        SpeakerRole.SELF,
                        speakingRatio,
                        wordCount,
                        speakingDurationMs,
                        syllableCount
                )),
                List.of(speakerInsight(SpeakerRole.SELF, expression, expressionCount)),
                List.of(spiciness(SpeakerRole.SELF, swearWordCount))
        );
    }

    private AnalysisReport report(
            Long recordingId,
            AnalysisScenario scenario,
            List<SpeakerMetrics> metrics,
            List<QualitativeAnalysis.SpeakerInsight> speakerInsights,
            List<QualitativeAnalysis.SpicinessInsight> spicinessInsights
    ) {
        return new AnalysisReport(
                new AnalysisReport.RecordingInfo(
                        recordingId,
                        scenario,
                        600_000L,
                        scenario.requiredRoles().stream()
                                .map(role -> new AnalysisSpeaker(role, role.name()))
                                .toList()
                ),
                new AnalysisReport.QuantitativeAnalysis(metrics, null),
                new AnalysisReport.QualitativeReport(
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        speakerInsights,
                        List.of(),
                        spicinessInsights,
                        List.of(),
                        List.of()
                )
        );
    }

    private SpeakerMetrics metrics(
            SpeakerRole speakerRole,
            int speakingRatio,
            long wordCount,
            long speakingDurationMs,
            long syllableCount
    ) {
        return new SpeakerMetrics(
                speakerRole,
                speakingDurationMs,
                10,
                100,
                syllableCount,
                wordCount,
                BigDecimal.valueOf(speakingRatio),
                BigDecimal.valueOf(6_000),
                BigDecimal.valueOf(100),
                BigDecimal.valueOf(syllableCount)
                        .multiply(BigDecimal.valueOf(1_000))
                        .divide(
                                BigDecimal.valueOf(speakingDurationMs),
                                2,
                                java.math.RoundingMode.HALF_UP
                        )
        );
    }

    private QualitativeAnalysis.SpeakerInsight speakerInsight(
            SpeakerRole speakerRole,
            String expression,
            int count
    ) {
        return new QualitativeAnalysis.SpeakerInsight(
                speakerRole,
                List.of(),
                null,
                List.of(new QualitativeAnalysis.FrequentExpression(
                        QualitativeAnalysis.FrequentExpressionCategory.WORD,
                        expression,
                        count,
                        List.of(1L)
                ))
        );
    }

    private QualitativeAnalysis.SpicinessInsight spiciness(
            SpeakerRole speakerRole,
            int count
    ) {
        return new QualitativeAnalysis.SpicinessInsight(
                speakerRole,
                count == 0 ? 0 : 20,
                "비속어 사용 요약",
                count == 0
                        ? List.of()
                        : List.of(new QualitativeAnalysis.SwearWordUsage(
                                "비속어",
                                count,
                                List.of(1L)
                        )),
                List.of()
        );
    }

    private record SummarySource(
            Long recordingId,
            LocalDateTime analyzedAt,
            ParentChildRole parentChildRole,
            String resultJson
    ) implements AnalysisSummarySourceProjection {

        @Override
        public Long getRecordingId() {
            return recordingId;
        }

        @Override
        public LocalDateTime getAnalyzedAt() {
            return analyzedAt;
        }

        @Override
        public ParentChildRole getParentChildRole() {
            return parentChildRole;
        }

        @Override
        public String getResultJson() {
            return resultJson;
        }
    }
}
