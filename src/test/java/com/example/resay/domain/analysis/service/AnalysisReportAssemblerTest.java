package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.dto.AnalysisReportDto;
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
import static com.example.resay.domain.analysis.support.AnalysisTestSpeakers.speakersFor;

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
                              "evidenceSegmentIds": [1, 2, 3]
                            }
                          ],
                          "topics": [
                            {
                              "title": "주말 계획",
                              "description": "주말 약속을 정했습니다.",
                              "segmentIds": [1, 3]
                            }
                          ],
                          "characterInsights": [],
                          "speakerInsights": [
                            {
                              "speakerRole": "SELF",
                              "patterns": [
                                {
                                  "category": "EXPRESSION_PATTERN",
                                  "title": "확인 표현",
                                  "description": "상대의 말을 확인하며 반응했어요.",
                                  "evidenceSegmentIds": [1, 3]
                                }
                              ],
                              "frequentExpressionSummary": {
                                "title": "주말 표현을 자주 사용해요",
                                "description": "대화 계획을 말할 때 주말이라는 단어를 반복했습니다.",
                                "evidenceSegmentIds": [1]
                              },
                              "frequentExpressions": [
                                {
                                  "category": "WORD",
                                  "expression": "주말",
                                  "count": 2,
                                  "evidenceSegmentIds": [1, 3]
                                }
                              ]
                            }
                          ],
                          "interestInsights": [],
                          "spicinessInsights": [],
                          "reactionStyleInsights": [],
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
        assertThat(json.recordingInfo().speakers())
                .extracting("speakerName")
                .containsExactly("호준", "호석");
        assertThat(json.quantitativeAnalysis().speakers()).hasSize(2);
        AnalysisReport.TimelineItem timeline = json.qualitativeAnalysis().timeline().get(0);
        assertThat(timeline.startMs()).isEqualTo(100L);
        assertThat(timeline.endMs()).isEqualTo(1_800L);
        AnalysisReport.TopicItem topic = json.qualitativeAnalysis().topics().get(0);
        assertThat(topic.turnCount()).isEqualTo(2);
        assertThat(topic.speakingDurationMs()).isEqualTo(700L);
        assertThat(topic.longest()).isTrue();
        assertThat(topic.timeRanges()).containsExactly(
                new AnalysisReport.TopicTimeRange(100L, 500L),
                new AnalysisReport.TopicTimeRange(1_500L, 1_800L)
        );
        assertThat(json.qualitativeAnalysis().speakerInsights().get(0)
                .frequentExpressions().get(0).count()).isEqualTo(2);
        assertThat(AnalysisReportDto.from(json).qualitativeAnalysis()
                .speakerInsights().get(0).patterns().get(0).count()).isEqualTo(2);
        assertThat(report.modelName()).isEqualTo("liner-mark-1.1");
        assertThat(report.schemaVersion()).isEqualTo("analysis-report-v8");
    }

    private AnalysisSource source() {
        return new AnalysisSource(
                10L,
                AnalysisScenario.FRIEND_DAILY,
                2_000L,
                speakersFor(AnalysisScenario.FRIEND_DAILY),
                List.of(
                        new AnalysisSegment(1L, SpeakerRole.SELF, 100L, 500L, "주말에 만날까?"),
                        new AnalysisSegment(2L, SpeakerRole.FRIEND, 700L, 1_200L, "좋아, 오후에 보자."),
                        new AnalysisSegment(3L, SpeakerRole.SELF, 1_500L, 1_800L, "주말 장소도 정하자.")
                )
        );
    }
}
