package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.service.AnalysisFailureCategory;
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
    void excludesRepresentativeEvidencesWhenOnlyTheyFailValidation() {
        AnalysisSource source = source();
        LinerChatRequest request = request();
        given(requestFactory.create(source)).willReturn(request);
        given(requestFactory.promptVersion()).willReturn("analysis-prompt-v2");
        given(requestFactory.schemaVersion()).willReturn("analysis-result-v2");
        given(linerApiClient.chat(request)).willReturn(result(
                contentWithRepresentativeEvidences(),
                "stop"
        ));
        willThrow(new LinerAnalysisException(
                "invalid_analysis_response",
                "대표 근거 검증 실패",
                null
        )).willDoNothing().given(responseValidator).validate(eq(source), any());

        AnalysisModelResult result = modelClient.analyze(source);

        assertThat(result.resultJson()).contains("\"representativeEvidences\":[]");
        then(responseValidator).should(org.mockito.Mockito.times(2))
                .validate(eq(source), any(QualitativeAnalysis.class));
    }

    @Test
    void doesNotHideCoreValidationFailureBehindRepresentativeEvidenceFallback() {
        AnalysisSource source = source();
        LinerChatRequest request = request();
        given(requestFactory.create(source)).willReturn(request);
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

        assertThatThrownBy(() -> modelClient.analyze(source))
                .isSameAs(coreFailure)
                .hasMessageContaining("핵심 분석 검증 실패");

        then(responseValidator).should(org.mockito.Mockito.times(2))
                .validate(eq(source), any(QualitativeAnalysis.class));
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
}
