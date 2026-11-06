package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.service.AnalysisFailureCategory;
import com.example.resay.domain.analysis.service.AnalysisReportAssembler;
import com.example.resay.domain.analysis.service.ConversationMetricsCalculator;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.example.resay.domain.analysis.support.AnalysisTestSpeakers.speakersFor;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class LinerAnalysisModelClientTest {

    @Mock
    private LinerApiClient linerApiClient;

    @Mock
    private LinerAnalysisRequestFactory requestFactory;

    @Mock
    private LinerAnalysisResponseValidator responseValidator;

    private LinerAnalysisModelClient modelClient;

    @BeforeEach
    void setUp() {
        modelClient = new LinerAnalysisModelClient(
                linerApiClient,
                requestFactory,
                new LinerAnalysisResponseGrounder(),
                responseValidator,
                new ObjectMapper()
        );
    }

    @Test
    void analyzesSourceAndReturnsVersionedModelResult() {
        AnalysisSource source = source();
        LinerChatRequest request = request();
        given(requestFactory.create(source)).willReturn(request);
        given(requestFactory.promptVersion()).willReturn("analysis-prompt-v2");
        given(requestFactory.schemaVersion()).willReturn("analysis-result-v2");
        given(linerApiClient.chat(request)).willReturn(result(validContent(), "stop"));

        AnalysisModelResult result = modelClient.analyze(source);

        assertThat(result.modelName()).isEqualTo("liner-mark-1.1");
        assertThat(result.promptVersion()).isEqualTo("analysis-prompt-v2");
        assertThat(result.schemaVersion()).isEqualTo("analysis-result-v2");
        assertThat(result.resultJson())
                .contains("\"overview\"")
                .contains("\"interestInsights\"")
                .contains("\"evidenceSegmentIds\":[1,2]");
        then(responseValidator).should().validate(
                eq(source),
                any(QualitativeAnalysis.class)
        );
    }

    @Test
    void preservesRepresentativeEvidencesWhenValidationFails() {
        AnalysisSource source = source();
        LinerChatRequest request = request();
        given(requestFactory.create(source)).willReturn(request);
        given(requestFactory.promptVersion()).willReturn("analysis-prompt-v2");
        given(requestFactory.schemaVersion()).willReturn("analysis-result-v2");
        given(linerApiClient.chat(request)).willReturn(result(
                contentWithRepresentativeEvidences(),
                "stop"
        ));
        LinerAnalysisException representativeFailure = new LinerAnalysisException(
                "invalid_analysis_response",
                "대표 근거 검증 실패",
                null
        );
        willThrow(representativeFailure)
                .given(responseValidator).validate(eq(source), any());

        AnalysisModelResult result = modelClient.analyze(source);

        assertThat(result.resultJson())
                .contains("\"representativeEvidences\":[{")
                .contains("\"segmentId\":1");
        then(responseValidator).should()
                .validate(eq(source), any(QualitativeAnalysis.class));
    }

    @Test
    void preservesRepresentativeEvidencesWhenDifferentSemanticValidationFails() {
        AnalysisSource source = source();
        LinerChatRequest request = request();
        given(requestFactory.create(source)).willReturn(request);
        given(requestFactory.promptVersion()).willReturn("analysis-prompt-v15");
        given(requestFactory.schemaVersion()).willReturn("analysis-result-v9");
        given(linerApiClient.chat(request)).willReturn(result(
                contentWithRepresentativeEvidences(),
                "stop"
        ));
        LinerAnalysisException coreFailure = new LinerAnalysisException(
                "invalid_analysis_response",
                "핵심 분석 검증 실패",
                null
        );
        willThrow(coreFailure).given(responseValidator).validate(eq(source), any());

        AnalysisModelResult result = modelClient.analyze(source);

        assertThat(result.resultJson())
                .contains("\"overview\"")
                .contains("\"representativeEvidences\":[{")
                .contains("\"segmentId\":1");
        assertThat(result.promptVersion()).isEqualTo("analysis-prompt-v15");

        then(responseValidator).should()
                .validate(eq(source), any(QualitativeAnalysis.class));
    }

    @Test
    void preservesOptionalReportFieldsWhenTheirValidationFails() {
        AnalysisSource source = source();
        LinerChatRequest request = request();
        given(requestFactory.create(source)).willReturn(request);
        given(requestFactory.promptVersion()).willReturn("analysis-prompt-v15");
        given(requestFactory.schemaVersion()).willReturn("analysis-result-v9");
        given(linerApiClient.chat(request)).willReturn(result(
                contentWithOptionalReportFields(),
                "stop"
        ));
        willThrow(new LinerAnalysisException(
                "invalid_analysis_response",
                "부가 보고서 검증 실패",
                null
        )).willDoNothing().given(responseValidator).validate(eq(source), any());

        AnalysisModelResult result = modelClient.analyze(source);

        assertThat(result.resultJson())
                .contains("\"relationshipSummary\":{")
                .contains("\"reportMetrics\":[{")
                .contains("\"speakerTendencies\":[]")
                .contains("\"coachingSuggestions\":[]");
        then(responseValidator).should()
                .validate(eq(source), any(QualitativeAnalysis.class));
    }

    @Test
    void preservesRequiredParentChildCoachingWhenOtherOptionalFieldsFail() {
        AnalysisSource source = parentChildSource();
        LinerChatRequest request = request();
        given(requestFactory.create(source)).willReturn(request);
        given(requestFactory.promptVersion()).willReturn("analysis-prompt-v18");
        given(requestFactory.schemaVersion()).willReturn("analysis-report-v12");
        given(linerApiClient.chat(request)).willReturn(result(
                parentChildContentWithRenderableCore(),
                "stop"
        ));
        willThrow(new LinerAnalysisException(
                "invalid_analysis_response",
                "부가 보고서 검증 실패",
                null
        )).willDoNothing().given(responseValidator).validate(eq(source), any());

        AnalysisModelResult result = modelClient.analyze(source);

        assertThat(result.resultJson())
                .contains("\"relationshipSummary\":{")
                .contains("\"reportMetrics\":[{")
                .contains("\"coachingSuggestions\":[{")
                .contains("\"order\":1")
                .contains("\"order\":2")
                .contains("\"order\":3")
                .contains("입장 확인: 학교생활");
        then(responseValidator).should()
                .validate(eq(source), any(QualitativeAnalysis.class));
    }

    @Test
    void doesNotRelaxNonValidationFailure() {
        AnalysisSource source = source();
        LinerChatRequest request = request();
        given(requestFactory.create(source)).willReturn(request);
        given(linerApiClient.chat(request)).willReturn(result(validContent(), "stop"));
        LinerAnalysisException configurationFailure = new LinerAnalysisException(
                AnalysisFailureCategory.PROVIDER_CONFIGURATION,
                "not_configured",
                "설정 오류",
                null
        );
        willThrow(configurationFailure).given(responseValidator).validate(eq(source), any());

        assertThatThrownBy(() -> modelClient.analyze(source))
                .isSameAs(configurationFailure);
    }

    @Test
    void rejectsBestEffortResponseWhenCoreReportCannotBeRendered() {
        AnalysisSource source = source();
        LinerChatRequest request = request();
        given(requestFactory.create(source)).willReturn(request);
        given(linerApiClient.chat(request)).willReturn(result(contentWithoutOverview(), "stop"));
        LinerAnalysisException validationFailure = new LinerAnalysisException(
                "invalid_analysis_response",
                "핵심 보고서 구조 오류",
                null
        );
        willThrow(validationFailure).given(responseValidator).validate(eq(source), any());

        assertThatThrownBy(() -> modelClient.analyze(source))
                .isSameAs(validationFailure);
    }

    @Test
    void preservesValidOptionalFieldsWhenActualParentChildValidationFallsBack()
            throws Exception {
        AnalysisSource source = parentChildSource();
        LinerChatRequest request = request();
        given(requestFactory.create(source)).willReturn(request);
        given(requestFactory.promptVersion()).willReturn("analysis-prompt-v15");
        given(requestFactory.schemaVersion()).willReturn("analysis-result-v9");
        given(linerApiClient.chat(request)).willReturn(result(
                parentChildContentWithRenderableCore(),
                "stop"
        ));
        ObjectMapper objectMapper = new ObjectMapper();
        LinerAnalysisModelClient actualValidatorClient = new LinerAnalysisModelClient(
                linerApiClient,
                requestFactory,
                new LinerAnalysisResponseGrounder(),
                new LinerAnalysisResponseValidator(),
                objectMapper
        );

        AnalysisModelResult qualitativeResult = actualValidatorClient.analyze(source);
        AnalysisModelResult reportResult = new AnalysisReportAssembler(objectMapper).assemble(
                source,
                new ConversationMetricsCalculator().calculate(source),
                qualitativeResult
        );
        AnalysisReport report = objectMapper.readValue(
                reportResult.resultJson(),
                AnalysisReport.class
        );

        assertThat(report.qualitativeAnalysis().relationshipSummary()).isNotNull();
        assertThat(report.qualitativeAnalysis().reportMetrics())
                .singleElement()
                .satisfies(metric -> {
                    assertThat(metric.type())
                            .isEqualTo(QualitativeAnalysis.ReportMetricType.SITUATION_QUESTION);
                    assertThat(metric.value()).isEqualTo(1);
                });
        assertThat(report.qualitativeAnalysis().coachingSuggestions())
                .extracting(QualitativeAnalysis.CoachingSuggestion::order)
                .containsExactly(1, 2, 3);
        assertThat(report.qualitativeAnalysis().coachingSuggestions().get(0).title())
                .isEqualTo("입장 확인: 학교생활");
        assertThat(report.qualitativeAnalysis().representativeEvidences())
                .extracting(AnalysisReport.RepresentativeEvidence::type)
                .containsExactly(
                        QualitativeAnalysis.ReportEvidenceType.CHILD_POSITION,
                        QualitativeAnalysis.ReportEvidenceType.FOLLOWING_ADVICE,
                        QualitativeAnalysis.ReportEvidenceType.UNASKED_QUESTION,
                        QualitativeAnalysis.ReportEvidenceType.WORRY_START,
                        QualitativeAnalysis.ReportEvidenceType.EXPRESSION_TURNING_POINT,
                        QualitativeAnalysis.ReportEvidenceType.CLOSING_RESPONSE
                );
    }

    @Test
    void rejectsMalformedJsonResponse() {
        AnalysisSource source = source();
        LinerChatRequest request = request();
        given(requestFactory.create(source)).willReturn(request);
        given(linerApiClient.chat(request)).willReturn(result("not-json", "stop"));

        assertThatThrownBy(() -> modelClient.analyze(source))
                .isInstanceOfSatisfying(LinerAnalysisException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo("invalid_analysis_response");
                    assertThat(exception.getCategory())
                            .isEqualTo(AnalysisFailureCategory.RESPONSE_FORMAT_ERROR);
                });

        then(responseValidator).shouldHaveNoInteractions();
    }

    @Test
    void rejectsIncompleteCompletion() {
        AnalysisSource source = source();
        LinerChatRequest request = request();
        given(requestFactory.create(source)).willReturn(request);
        given(linerApiClient.chat(request)).willReturn(result(validContent(), "length"));

        assertThatThrownBy(() -> modelClient.analyze(source))
                .isInstanceOf(LinerAnalysisException.class)
                .satisfies(exception -> assertThat(((LinerAnalysisException) exception).getCategory())
                        .isEqualTo(AnalysisFailureCategory.RESPONSE_LENGTH_EXCEEDED))
                .hasMessageContaining("정상적으로 완료되지 않았습니다")
                .hasMessageContaining("finishReason=length")
                .hasMessageContaining("completionTokens=20");

        then(responseValidator).should(never()).validate(
                any(),
                any()
        );
    }

    @Test
    void classifiesProviderAuthenticationWithoutExposingProviderMessage() {
        AnalysisSource source = source();
        LinerChatRequest request = request();
        given(requestFactory.create(source)).willReturn(request);
        given(linerApiClient.chat(request)).willThrow(new LinerApiException(
                401,
                "invalid_api_key",
                false,
                null,
                "request-401",
                "secret provider detail",
                null
        ));

        assertThatThrownBy(() -> modelClient.analyze(source))
                .isInstanceOfSatisfying(LinerAnalysisException.class, exception -> {
                    assertThat(exception.getCategory())
                            .isEqualTo(AnalysisFailureCategory.PROVIDER_AUTHENTICATION);
                    assertThat(exception.getMessage()).doesNotContain("secret provider detail");
                });
    }

    @Test
    void classifiesProviderRateLimit() {
        AnalysisSource source = source();
        LinerChatRequest request = request();
        given(requestFactory.create(source)).willReturn(request);
        given(linerApiClient.chat(request)).willThrow(new LinerApiException(
                429,
                "rate_limited",
                true,
                Duration.ofSeconds(1),
                "request-429",
                null
        ));

        assertThatThrownBy(() -> modelClient.analyze(source))
                .isInstanceOfSatisfying(LinerAnalysisException.class, exception ->
                        assertThat(exception.getCategory())
                                .isEqualTo(AnalysisFailureCategory.PROVIDER_RATE_LIMIT)
                );
    }

    private AnalysisSource source() {
        return new AnalysisSource(
                1L,
                AnalysisScenario.FRIEND_DAILY,
                10_000L,
                speakersFor(AnalysisScenario.FRIEND_DAILY),
                List.of(
                        new AnalysisSegment(1L, SpeakerRole.SELF, 100L, 500L, "오늘 뭐 했어?"),
                        new AnalysisSegment(2L, SpeakerRole.FRIEND, 600L, 900L, "학교 갔다 왔어")
                )
        );
    }

    private AnalysisSource parentChildSource() {
        return new AnalysisSource(
                2L,
                AnalysisScenario.PARENT_CHILD_CONFLICT,
                10_000L,
                speakersFor(AnalysisScenario.PARENT_CHILD_CONFLICT),
                List.of(
                        new AnalysisSegment(1L, SpeakerRole.PARENT, 100L, 1_500L,
                                "오늘 학교에서 무슨 일이 있었는지 말해 줄래?"),
                        new AnalysisSegment(2L, SpeakerRole.CHILD, 1_600L, 2_000L, "그냥 그랬어")
                )
        );
    }

    private LinerChatRequest request() {
        return new LinerChatRequest(
                "liner-mark-1.1",
                List.of(new LinerChatMessage("user", "input")),
                false,
                8192,
                "high",
                Map.of("type", "json_schema")
        );
    }

    private LinerChatResult result(String content, String finishReason) {
        return new LinerChatResult(
                "request-1",
                new LinerChatResponse(
                        "completion-1",
                        "liner-mark-1.1",
                        List.of(new LinerChatResponse.Choice(
                                0,
                                new LinerChatMessage("assistant", content),
                                finishReason
                        )),
                        new LinerChatResponse.Usage(100, 20, 120)
                )
        );
    }

    private String validContent() {
        return """
                {
                  "overview": {
                    "title": "학교 근황 대화",
                    "description": "학교에서 있었던 일을 묻고 답했어요.",
                    "evidenceSegmentIds": [1, 2]
                  },
                  "timeline": [
                    {
                      "title": "근황 묻기",
                      "description": "오늘 한 일을 물었어요.",
                      "evidenceSegmentIds": [1, 2]
                    }
                  ],
                  "topics": [
                    {
                      "title": "학교 이야기",
                      "description": "학교에서 있었던 일을 이야기했어요.",
                      "segmentIds": [1, 2]
                    }
                  ],
                  "characterInsights": [],
                  "speakerInsights": [
                    {
                      "speakerRole": "SELF",
                      "patterns": [],
                      "frequentExpressionSummary": null,
                      "frequentExpressions": []
                    },
                    {
                      "speakerRole": "FRIEND",
                      "patterns": [],
                      "frequentExpressionSummary": null,
                      "frequentExpressions": []
                    }
                  ],
                  "interestInsights": [],
                  "spicinessInsights": [],
                  "reactionStyleInsights": [],
                  "scenarioInsights": []
                }
                """;
    }

    private String contentWithRepresentativeEvidences() {
        return """
                {
                  "overview": {
                    "title": "학교 근황 대화",
                    "description": "학교에서 있었던 일을 묻고 답했어요.",
                    "evidenceSegmentIds": [1, 2]
                  },
                  "timeline": [
                    {
                      "title": "근황 묻기",
                      "description": "오늘 한 일을 물었어요.",
                      "evidenceSegmentIds": [1, 2]
                    }
                  ],
                  "topics": [
                    {
                      "title": "학교 이야기",
                      "description": "학교에서 있었던 일을 이야기했어요.",
                      "segmentIds": [1, 2]
                    }
                  ],
                  "characterInsights": [],
                  "speakerInsights": [],
                  "interestInsights": [],
                  "spicinessInsights": [],
                  "reactionStyleInsights": [
                    {
                      "speakerRole": "SELF",
                      "thinkingPercent": 50,
                      "feelingPercent": 50,
                      "description": "질문으로 반응했어요.",
                      "examples": [
                        {
                          "category": "THINKING",
                          "title": "질문",
                          "description": "근황을 물었어요.",
                          "evidenceSegmentIds": [1]
                        }
                      ]
                    },
                    {
                      "speakerRole": "FRIEND",
                      "thinkingPercent": 50,
                      "feelingPercent": 50,
                      "description": "사실을 답했어요.",
                      "examples": [
                        {
                          "category": "THINKING",
                          "title": "대답",
                          "description": "학교 이야기를 했어요.",
                          "evidenceSegmentIds": [2]
                        }
                      ]
                    }
                  ],
                  "scenarioInsights": [],
                  "representativeEvidences": [
                    {
                      "type": "REACTION_STYLE_EXAMPLE",
                      "speakerRole": "SELF",
                      "reactionCategory": "THINKING",
                      "segmentId": 1,
                      "displayContent": "오늘 뭐 했어?"
                    },
                    {
                      "type": "REACTION_STYLE_EXAMPLE",
                      "speakerRole": "FRIEND",
                      "reactionCategory": "THINKING",
                      "segmentId": 2,
                      "displayContent": "학교 갔다 왔어"
                    }
                  ]
                }
                """;
    }

    private String contentWithOptionalReportFields() {
        return validContent().replace(
                "\"scenarioInsights\": []",
                """
                "scenarioInsights": [],
                "relationshipSummary": {
                  "title": "편안한 대화",
                  "description": "학교 이야기를 주고받았어요.",
                  "evidenceSegmentIds": [1, 2]
                },
                "reportMetrics": [
                  {
                    "type": "COMPLIMENT",
                    "speakerRole": "SELF",
                    "value": 1,
                    "unit": "COUNT",
                    "title": "칭찬",
                    "description": "칭찬 표현을 사용했어요.",
                    "evidenceSegmentIds": [1],
                    "displayEvidences": []
                  }
                ],
                "speakerTendencies": [],
                "coachingSuggestions": []
                """
        );
    }

    private String contentWithoutOverview() {
        return validContent().replaceFirst(
                "(?s)\"overview\"\\s*:\\s*\\{.*?}\\s*,",
                "\"overview\": null,"
        );
    }

    private String parentChildContentWithRenderableCore() {
        return """
                {
                  "overview": {
                    "title": "학교 이야기",
                    "description": "학교에서 있었던 일을 물어봤어요.",
                    "evidenceSegmentIds": [1, 2]
                  },
                  "timeline": [
                    {
                      "title": "대화 시작",
                      "description": "부모가 학교생활을 물어봤어요.",
                      "evidenceSegmentIds": [1, 2]
                    }
                  ],
                  "topics": [
                    {
                      "title": "학교생활",
                      "description": "학교에서 있었던 일을 이야기했어요.",
                      "segmentIds": [1, 2]
                    }
                  ],
                  "characterInsights": [],
                  "speakerInsights": [],
                  "interestInsights": [],
                  "spicinessInsights": [],
                  "reactionStyleInsights": [],
                  "scenarioInsights": [],
                  "representativeEvidences": [],
                  "relationshipSummary": {
                    "title": "조심스러운 문답",
                    "description": "부모가 묻고 자녀가 짧게 답했어요.",
                    "evidenceSegmentIds": [1, 2]
                  },
                  "reportMetrics": [
                    {
                      "type": "SITUATION_QUESTION",
                      "speakerRole": "PARENT",
                      "value": 1,
                      "unit": "COUNT",
                      "title": "상황 질문",
                      "description": "학교에서 있었던 일을 물어봤어요.",
                      "evidenceSegmentIds": [1],
                      "displayEvidences": []
                    }
                  ],
                  "speakerTendencies": [],
                  "coachingSuggestions": []
                }
                """;
    }
}
