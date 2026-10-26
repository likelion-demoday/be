package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static com.example.resay.domain.analysis.support.AnalysisTestSpeakers.speakersFor;
import static org.assertj.core.api.Assertions.assertThat;

class LinerAnalysisReviewRequestFactoryTest {

    private ObjectMapper objectMapper;
    private LinerAnalysisReviewRequestFactory reviewRequestFactory;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        LinerProperties properties = new LinerProperties(
                "test-key",
                "https://platform.liner.com",
                "liner-mark-1.1",
                Duration.ofSeconds(3),
                Duration.ofSeconds(120)
        );
        LinerAnalysisRequestFactory initialRequestFactory =
                new LinerAnalysisRequestFactory(properties, objectMapper);
        reviewRequestFactory = new LinerAnalysisReviewRequestFactory(
                properties,
                initialRequestFactory,
                objectMapper
        );
    }

    @Test
    void createsReviewRequestWithConversationAndDraft() throws Exception {
        AnalysisSource source = source();
        QualitativeAnalysis draft = objectMapper.readValue(
                minimalDraftJson(),
                QualitativeAnalysis.class
        );

        LinerChatRequest request = reviewRequestFactory.create(source, draft);

        assertThat(request.model()).isEqualTo("liner-mark-1.1");
        assertThat(request.messages()).hasSize(2);
        assertThat(request.messages().get(0).content())
                .contains("conversation의 segments가 유일한 사실 근거")
                .contains("초안을 그대로 승인하지 말고")
                .contains("완전한 최종 결과 한 개")
                .contains("frequentExpressions가 비어 있지 않으면")
                .contains("교차 필드 조건을 화자별로 다시 확인")
                .contains("수식어와 역할·사물 명사를 결합한")
                .contains("친구 사이의 일상 대화입니다");

        Map<?, ?> input = objectMapper.readValue(
                request.messages().get(1).content(),
                Map.class
        );
        Map<?, ?> conversation = (Map<?, ?>) input.get("conversation");
        Map<?, ?> draftAnalysis = (Map<?, ?>) input.get("draftAnalysis");
        assertThat(conversation.get("recordingId")).isEqualTo(1);
        assertThat(conversation.get("scenario")).isEqualTo("FRIEND_DAILY");
        assertThat((List<?>) conversation.get("segments")).hasSize(2);
        assertThat(draftAnalysis.containsKey("overview")).isTrue();
        assertThat(request.responseFormat()).containsEntry("type", "json_schema");
        assertThat(reviewRequestFactory.promptVersion())
                .isEqualTo("analysis-prompt-v11+analysis-review-v1");
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

    private String minimalDraftJson() {
        return """
                {
                  "overview": {
                    "title": "학교 근황 대화",
                    "description": "학교에서 있었던 일을 묻고 답했어요.",
                    "evidenceSegmentIds": [1, 2]
                  },
                  "timeline": [],
                  "topics": [],
                  "characterInsights": [],
                  "speakerInsights": [],
                  "interestInsights": [],
                  "spicinessInsights": [],
                  "reactionStyleInsights": [],
                  "scenarioInsights": []
                }
                """;
    }
}
