package com.example.resay.domain.analysis.dto;

import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.List;
import org.junit.jupiter.api.Test;

import static com.example.resay.domain.analysis.support.AnalysisTestSpeakers.speakersFor;
import static org.assertj.core.api.Assertions.assertThat;

class AnalysisReportDtoTest {

    @Test
    void exposesAffectionSummaryForEachSpeaker() {
        AnalysisReport report = new AnalysisReport(
                new AnalysisReport.RecordingInfo(
                        1L,
                        AnalysisScenario.COUPLE_DAILY,
                        1_000L,
                        speakersFor(AnalysisScenario.COUPLE_DAILY)
                ),
                new AnalysisReport.QuantitativeAnalysis(List.of(), null, 0),
                new AnalysisReport.QualitativeReport(
                        new QualitativeAnalysis.Overview("애정 표현", "애정 표현을 나눴어요.", List.of(1L)),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        List.of(
                                new QualitativeAnalysis.SpeakerAffectionSummary(
                                        SpeakerRole.SELF,
                                        "직접 전하는 마음",
                                        "보고 싶다는 말로 마음을 표현해요.",
                                        List.of(1L)
                                ),
                                new QualitativeAnalysis.SpeakerAffectionSummary(
                                        SpeakerRole.PARTNER,
                                        "세심한 챙김",
                                        "식사를 확인하며 상대를 챙겨요.",
                                        List.of(2L)
                                )
                        ),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );

        assertThat(AnalysisReportDto.from(report).qualitativeAnalysis()
                .speakerAffectionSummaries())
                .extracting(
                        AnalysisReportDto.SpeakerAffectionSummary::speakerRole,
                        AnalysisReportDto.SpeakerAffectionSummary::title
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                SpeakerRole.SELF,
                                "직접 전하는 마음"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                SpeakerRole.PARTNER,
                                "세심한 챙김"
                        )
                );
    }

    @Test
    void alwaysExposesExactlyFourNormalizedCommonSentenceMetricsPerSpeaker() {
        AnalysisReport report = new AnalysisReport(
                new AnalysisReport.RecordingInfo(
                        1L,
                        AnalysisScenario.FRIEND_DAILY,
                        1_000L,
                        List.of()
                ),
                new AnalysisReport.QuantitativeAnalysis(List.of(), null, 0),
                new AnalysisReport.QualitativeReport(
                        new QualitativeAnalysis.Overview("요약", "설명", List.of()),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        List.of(),
                        List.of(
                                new AnalysisReport.ReportMetric(
                                        QualitativeAnalysis.ReportMetricType.SENTENCE_COMPLETENESS,
                                        SpeakerRole.SELF,
                                        140,
                                        QualitativeAnalysis.ReportMetricUnit.COUNT,
                                        "잘못된 제목",
                                        "관찰 설명",
                                        List.of(),
                                        List.of()
                                ),
                                new AnalysisReport.ReportMetric(
                                        QualitativeAnalysis.ReportMetricType.SENTENCE_COMPLETENESS,
                                        SpeakerRole.SELF,
                                        30,
                                        QualitativeAnalysis.ReportMetricUnit.PERCENT,
                                        "중복 제목",
                                        "중복 설명",
                                        List.of(),
                                        List.of()
                                ),
                                new AnalysisReport.ReportMetric(
                                        QualitativeAnalysis.ReportMetricType.COMPLIMENT,
                                        SpeakerRole.SELF,
                                        2,
                                        QualitativeAnalysis.ReportMetricUnit.COUNT,
                                        "칭찬",
                                        "칭찬 표현이 두 번 나타났어요.",
                                        List.of(),
                                        List.of()
                                )
                        ),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );

        List<AnalysisReportDto.ReportMetric> metrics = AnalysisReportDto.from(report)
                .qualitativeAnalysis()
                .reportMetrics();

        assertThat(metrics).hasSize(9);
        assertThat(metrics)
                .extracting(
                        AnalysisReportDto.ReportMetric::speakerRole,
                        AnalysisReportDto.ReportMetric::type
                )
                .doesNotHaveDuplicates();
        List<AnalysisReportDto.ReportMetric> commonMetrics = metrics.stream()
                .filter(metric -> metric.unit()
                        == QualitativeAnalysis.ReportMetricUnit.PERCENT)
                .toList();
        assertThat(commonMetrics)
                .extracting(AnalysisReportDto.ReportMetric::unit)
                .containsOnly(QualitativeAnalysis.ReportMetricUnit.PERCENT);
        assertThat(commonMetrics.get(0))
                .satisfies(metric -> {
                    assertThat(metric.type()).isEqualTo(
                            QualitativeAnalysis.ReportMetricType.SENTENCE_COMPLETENESS
                    );
                    assertThat(metric.value()).isEqualTo(30);
                    assertThat(metric.title()).isEqualTo("완결성");
                    assertThat(metric.description()).isEqualTo("중복 설명");
                });
        assertThat(commonMetrics.subList(1, commonMetrics.size()))
                .extracting(AnalysisReportDto.ReportMetric::value)
                .containsOnly(0);
        assertThat(metrics)
                .filteredOn(metric -> metric.unit()
                        == QualitativeAnalysis.ReportMetricUnit.COUNT)
                .singleElement()
                .satisfies(metric -> {
                    assertThat(metric.type())
                            .isEqualTo(QualitativeAnalysis.ReportMetricType.COMPLIMENT);
                    assertThat(metric.value()).isEqualTo(2);
                });
    }
}
