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
}
