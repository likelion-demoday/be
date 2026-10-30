package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.dto.AnalysisReportDto;
import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.ConversationMetrics;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.resay.domain.analysis.support.AnalysisTestSpeakers.speakersFor;

class AnalysisReportAssemblerTest {

    private static final String LONG_CHARACTER_NAME =
            "상대의 이야기를 끝까지 따라가는 질문 수집가";
    private static final String LONG_CHARACTER_DESCRIPTION =
            "상대가 들려주는 이야기를 끝까지 듣고 새로운 질문으로 대화를 자연스럽게 이어 가요.";

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
                          "characterInsights": [
                            {
                              "speakerRole": "SELF",
                              "name": "상대의 이야기를 끝까지 따라가는 질문 수집가",
                              "description": "상대가 들려주는 이야기를 끝까지 듣고 새로운 질문으로 대화를 자연스럽게 이어 가요.",
                              "evidenceSegmentIds": [1]
                            }
                          ],
                          "speakerInsights": [
                            {
                              "speakerRole": "SELF",
                              "patterns": [
                                {
                                  "category": "EXPRESSION_PATTERN",
                                  "title": "확인 표현",
                                  "description": "상대의 말을 확인하며 반응했어요.",
                                  "evidenceSegmentIds": [1, 3],
                                  "displayEvidences": [
                                    {
                                      "segmentId": 1,
                                      "displayContent": "주말에 만날까?"
                                    }
                                  ]
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
                          "scenarioInsights": [],
                          "representativeEvidences": [
                            {
                              "type": "REACTION_STYLE_EXAMPLE",
                              "speakerRole": "SELF",
                              "reactionCategory": "THINKING",
                              "segmentId": 1,
                              "displayContent": "주말에 만날까?"
                            }
                          ],
                          "relationshipSummary": {
                            "title": "계획하는 사람 × 호응하는 사람",
                            "description": "주말 계획을 함께 구체화해요.",
                            "evidenceSegmentIds": [1, 2]
                          },
                          "reportMetrics": [
                            {
                              "type": "POSITION_EXPLANATION",
                              "speakerRole": "SELF",
                              "value": 2,
                              "unit": "COUNT",
                              "title": "응원 표현",
                              "description": "대화를 이어 주는 표현이 보여요.",
                              "evidenceSegmentIds": [1, 3],
                              "displayEvidences": [
                                {
                                  "segmentId": 1,
                                  "displayContent": "주말에 만날까?"
                                }
                              ]
                            }
                          ],
                          "speakerTendencies": [],
                          "coachingSuggestions": []
                        }
                        """,
                "liner-mark-1.1",
                "prompt-v1",
                "schema-v1"
        );

        AnalysisModelResult report = assembler.assemble(source, metrics, qualitative);

        AnalysisReport json = objectMapper.readValue(report.resultJson(), AnalysisReport.class);
        assertThat(json.recordingInfo().recordingId()).isEqualTo(10L);
        assertThat(json.recordingInfo().recordedAt())
                .isEqualTo(LocalDateTime.of(2026, 10, 28, 12, 30));
        assertThat(json.recordingInfo().speakers())
                .extracting("speakerName")
                .containsExactly("호준", "호석");
        assertThat(json.quantitativeAnalysis().speakers()).hasSize(2);
        assertThat(json.quantitativeAnalysis().totalTurnCount()).isEqualTo(3);
        assertThat(json.qualitativeAnalysis().characterInsights().get(0).name())
                .isEqualTo(LONG_CHARACTER_NAME);
        assertThat(json.qualitativeAnalysis().characterInsights().get(0).description())
                .isEqualTo(LONG_CHARACTER_DESCRIPTION);
        assertThat(AnalysisReportDto.from(json).qualitativeAnalysis()
                .characterInsights().get(0).name()).isEqualTo(LONG_CHARACTER_NAME);
        assertThat(AnalysisReportDto.from(json).qualitativeAnalysis()
                .characterInsights().get(0).description())
                .isEqualTo(LONG_CHARACTER_DESCRIPTION);
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
        AnalysisReportDto.EvidenceQuote patternEvidence = AnalysisReportDto.from(json)
                .qualitativeAnalysis().speakerInsights().get(0)
                .patterns().get(0).evidenceQuotes().get(0);
        assertThat(patternEvidence.originalContent()).isEqualTo("주말에 만날가?");
        assertThat(patternEvidence.displayContent()).isEqualTo("주말에 만날까?");
        assertThat(patternEvidence.correctionApplied()).isTrue();
        assertThat(patternEvidence.startMs()).isEqualTo(100L);
        AnalysisReport.RepresentativeEvidence representativeEvidence = json.qualitativeAnalysis()
                .representativeEvidences().get(0);
        assertThat(representativeEvidence.evidence().originalContent())
                .isEqualTo("주말에 만날가?");
        assertThat(representativeEvidence.evidence().displayContent())
                .isEqualTo("주말에 만날까?");
        assertThat(representativeEvidence.evidence().correctionApplied()).isTrue();
        assertThat(AnalysisReportDto.from(json).qualitativeAnalysis()
                .representativeEvidences().get(0).evidence().startMs()).isEqualTo(100L);
        assertThat(AnalysisReportDto.from(json).qualitativeAnalysis()
                .relationshipSummary().title()).contains("계획하는 사람");
        assertThat(AnalysisReportDto.from(json).qualitativeAnalysis()
                .reportMetrics().get(0).value()).isEqualTo(2);
        AnalysisReportDto.EvidenceQuote metricEvidence = AnalysisReportDto.from(json)
                .qualitativeAnalysis().reportMetrics().get(0).evidenceQuotes().get(0);
        assertThat(metricEvidence.originalContent()).isEqualTo("주말에 만날가?");
        assertThat(metricEvidence.displayContent()).isEqualTo("주말에 만날까?");
        assertThat(metricEvidence.correctionApplied()).isTrue();
        assertThat(metricEvidence.startMs()).isEqualTo(100L);
        assertThat(report.modelName()).isEqualTo("liner-mark-1.1");
        assertThat(report.schemaVersion()).isEqualTo("analysis-report-v11");
    }

    @Test
    void defaultsMetricEvidenceQuotesForLegacyReport() throws Exception {
        AnalysisReport.ReportMetric metric = objectMapper.readValue(
                """
                        {
                          "type": "POSITION_EXPLANATION",
                          "speakerRole": "SELF",
                          "value": 1,
                          "unit": "COUNT",
                          "title": "입장 설명",
                          "description": "입장을 설명했어요.",
                          "evidenceSegmentIds": [1]
                        }
                        """,
                AnalysisReport.ReportMetric.class
        );

        assertThat(metric.evidenceQuotes()).isEmpty();
    }

    private AnalysisSource source() {
        return new AnalysisSource(
                10L,
                AnalysisScenario.FRIEND_DAILY,
                2_000L,
                LocalDateTime.of(2026, 10, 28, 12, 30),
                speakersFor(AnalysisScenario.FRIEND_DAILY),
                List.of(
                        new AnalysisSegment(1L, SpeakerRole.SELF, 100L, 500L, "주말에 만날가?"),
                        new AnalysisSegment(2L, SpeakerRole.FRIEND, 700L, 1_200L, "좋아, 오후에 보자."),
                        new AnalysisSegment(3L, SpeakerRole.SELF, 1_500L, 1_800L, "주말 장소도 정하자.")
                )
        );
    }
}
