package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.ConversationMetrics;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
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

        JsonNode json = objectMapper.readTree(report.resultJson());
        assertThat(json.get("recordingInfo").get("recordingId").asLong()).isEqualTo(10L);
        assertThat(json.get("quantitativeAnalysis").get("speakers")).hasSize(2);
        JsonNode timeline = json.get("qualitativeAnalysis").get("timeline").get(0);
        assertThat(timeline.get("startMs").asLong()).isEqualTo(100L);
        assertThat(timeline.get("endMs").asLong()).isEqualTo(1_200L);
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
