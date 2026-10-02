package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.ConversationMetrics;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisReportAssemblerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConversationMetricsCalculator metricsCalculator = new ConversationMetricsCalculator();
    private final AnalysisReportAssembler assembler = new AnalysisReportAssembler(objectMapper);

    @Test
    void combinesQuantitativeAndQualitativeResultsIntoReport() throws Exception {
        AnalysisSource source = source();
        ConversationMetrics metrics = metricsCalculator.calculate(source);
        AnalysisModelResult qualitative = new AnalysisModelResult(
                """
                        {
                          "overview": {
                            "title": "주말 계획",
                            "description": "주말 약속을 정했습니다.",
                            "evidenceSegmentIds": [1, 2]
                          },
                          "timeline": [
                            {
                              "title": "약속 논의",
                              "description": "만날 시간을 정했습니다.",
                              "evidenceSegmentIds": [1, 2]
                            }
                          ],
                          "speakerInsights": [],
                          "scenarioInsights": []
                        }
                        """,
                "liner-mark-1.1",
                "prompt-v1",
                "schema-v1"
        );

        AnalysisModelResult report = assembler.assemble(source, metrics, qualitative);

        AnalysisReport json = objectMapper.readValue(report.resultJson(), AnalysisReport.class);
        assertThat(json.recordingInfo().recordingId()).isEqualTo(10L);
        assertThat(json.quantitativeAnalysis().speakers()).hasSize(2);
        AnalysisReport.TimelineItem timeline = json.qualitativeAnalysis().timeline().get(0);
        assertThat(timeline.startMs()).isEqualTo(100L);
        assertThat(timeline.endMs()).isEqualTo(1_200L);
        assertThat(report.modelName()).isEqualTo("liner-mark-1.1");
        assertThat(report.schemaVersion()).isEqualTo("analysis-report-v1");
    }

    private AnalysisSource source() {
        return new AnalysisSource(
                10L,
                AnalysisScenario.FRIEND_DAILY,
                2_000L,
                List.of(
                        new AnalysisSegment(1L, SpeakerRole.SELF, 100L, 500L, "주말에 만날까?"),
                        new AnalysisSegment(2L, SpeakerRole.FRIEND, 700L, 1_200L, "좋아, 오후에 보자.")
                )
        );
    }
}
