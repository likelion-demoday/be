package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
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
                org.mockito.ArgumentMatchers.eq(source),
                org.mockito.ArgumentMatchers.any(QualitativeAnalysis.class)
        );
    }

    @Test
    void rejectsMalformedJsonResponse() {
        AnalysisSource source = source();
        LinerChatRequest request = request();
        given(requestFactory.create(source)).willReturn(request);
        given(linerApiClient.chat(request)).willReturn(result("not-json", "stop"));

        assertThatThrownBy(() -> modelClient.analyze(source))
                .isInstanceOfSatisfying(LinerAnalysisException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo("invalid_analysis_response")
                );

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
                .hasMessageContaining("정상적으로 완료되지 않았습니다")
                .hasMessageContaining("finishReason=length")
                .hasMessageContaining("completionTokens=20");

        then(responseValidator).should(never()).validate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private AnalysisSource source() {
        return new AnalysisSource(
                1L,
                AnalysisScenario.FRIEND_DAILY,
                10_000L,
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
}
