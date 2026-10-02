package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LinerAnalysisResponseValidatorTest {

    private final LinerAnalysisResponseValidator validator = new LinerAnalysisResponseValidator();

    @Test
    void acceptsResponseGroundedInSourceSegments() {
        validator.validate(source(), validResponse());
    }

    @Test
    void rejectsUnknownEvidenceSegmentId() {
        QualitativeAnalysis response = new QualitativeAnalysis(
                new QualitativeAnalysis.Overview("요약", "대화 요약", List.of(999L)),
                validResponse().timeline(),
                validResponse().speakerInsights(),
                validResponse().scenarioInsights()
        );

        assertThatThrownBy(() -> validator.validate(source(), response))
                .isInstanceOfSatisfying(LinerAnalysisException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo("invalid_analysis_response")
                );
    }

    @Test
    void rejectsSpeakerRolesOutsideScenario() {
        QualitativeAnalysis response = new QualitativeAnalysis(
                validResponse().overview(),
                validResponse().timeline(),
                List.of(
                        new QualitativeAnalysis.SpeakerInsight(SpeakerRole.SELF, List.of()),
                        new QualitativeAnalysis.SpeakerInsight(SpeakerRole.PARTNER, List.of())
                ),
                validResponse().scenarioInsights()
        );

        assertThatThrownBy(() -> validator.validate(source(), response))
                .isInstanceOf(LinerAnalysisException.class)
                .hasMessageContaining("시나리오와 일치하지 않습니다");
    }

    @Test
    void rejectsTimelineOutsideChronologicalOrder() {
        QualitativeAnalysis response = new QualitativeAnalysis(
                validResponse().overview(),
                List.of(
                        new QualitativeAnalysis.TimelineItem("두 번째", "두 번째 주제", List.of(3L)),
                        new QualitativeAnalysis.TimelineItem("첫 번째", "첫 번째 주제", List.of(1L))
                ),
                validResponse().speakerInsights(),
                validResponse().scenarioInsights()
        );

        assertThatThrownBy(() -> validator.validate(source(), response))
                .isInstanceOf(LinerAnalysisException.class)
                .hasMessageContaining("시간순");
    }

    @Test
    void rejectsPatternWithoutEvidenceFromAttributedSpeaker() {
        QualitativeAnalysis.SpeakerPattern invalidPattern = new QualitativeAnalysis.SpeakerPattern(
                QualitativeAnalysis.SpeakerPatternCategory.FOLLOW_UP_QUESTION,
                "후속 질문",
                "질문으로 대화를 이어갔어요.",
                List.of(2L)
        );
        QualitativeAnalysis response = new QualitativeAnalysis(
                validResponse().overview(),
                validResponse().timeline(),
                List.of(
                        new QualitativeAnalysis.SpeakerInsight(
                                SpeakerRole.SELF,
                                List.of(invalidPattern)
                        ),
                        new QualitativeAnalysis.SpeakerInsight(SpeakerRole.FRIEND, List.of())
                ),
                validResponse().scenarioInsights()
        );

        assertThatThrownBy(() -> validator.validate(source(), response))
                .isInstanceOf(LinerAnalysisException.class)
                .hasMessageContaining("해당 화자의 근거 발화");
    }

    @Test
    void rejectsScenarioInsightCategoryOutsideScenario() {
        QualitativeAnalysis.ScenarioInsight conflictInsight =
                new QualitativeAnalysis.ScenarioInsight(
                        QualitativeAnalysis.ScenarioInsightCategory.CONFLICT_TOPIC,
                        List.of(SpeakerRole.SELF, SpeakerRole.FRIEND),
                        "갈등 주제",
                        "의견 차이가 나타났어요.",
                        List.of(1L, 2L)
                );
        QualitativeAnalysis response = new QualitativeAnalysis(
                validResponse().overview(),
                validResponse().timeline(),
                validResponse().speakerInsights(),
                List.of(conflictInsight)
        );

        assertThatThrownBy(() -> validator.validate(source(), response))
                .isInstanceOf(LinerAnalysisException.class)
                .hasMessageContaining("시나리오에 맞지 않는 상황별 관찰");
    }

    private AnalysisSource source() {
        return new AnalysisSource(
                1L,
                AnalysisScenario.FRIEND_DAILY,
                10_000L,
                List.of(
                        new AnalysisSegment(1L, SpeakerRole.SELF, 100L, 500L, "오늘 뭐 했어?"),
                        new AnalysisSegment(2L, SpeakerRole.FRIEND, 600L, 900L, "학교 갔다 왔어"),
                        new AnalysisSegment(3L, SpeakerRole.SELF, 1_000L, 1_400L, "재밌었어?")
                )
        );
    }

    private QualitativeAnalysis validResponse() {
        QualitativeAnalysis.SpeakerPattern questionPattern =
                new QualitativeAnalysis.SpeakerPattern(
                        QualitativeAnalysis.SpeakerPatternCategory.FOLLOW_UP_QUESTION,
                        "질문으로 이어가기",
                        "상대의 경험을 후속 질문으로 확인했어요.",
                        List.of(2L, 3L)
                );
        QualitativeAnalysis.ScenarioInsight commonInterest =
                new QualitativeAnalysis.ScenarioInsight(
                        QualitativeAnalysis.ScenarioInsightCategory.COMMON_INTEREST,
                        List.of(SpeakerRole.SELF, SpeakerRole.FRIEND),
                        "학교 이야기",
                        "학교에서 있었던 일을 중심으로 대화했어요.",
                        List.of(1L, 2L, 3L)
                );

        return new QualitativeAnalysis(
                new QualitativeAnalysis.Overview(
                        "학교 근황 대화",
                        "학교에서 있었던 일을 묻고 답했어요.",
                        List.of(1L, 2L)
                ),
                List.of(
                        new QualitativeAnalysis.TimelineItem(
                                "근황 묻기",
                                "오늘 한 일을 물었어요.",
                                List.of(1L, 2L)
                        ),
                        new QualitativeAnalysis.TimelineItem(
                                "경험 확인",
                                "경험에 관한 질문을 이어갔어요.",
                                List.of(3L)
                        )
                ),
                List.of(
                        new QualitativeAnalysis.SpeakerInsight(
                                SpeakerRole.SELF,
                                List.of(questionPattern)
                        ),
                        new QualitativeAnalysis.SpeakerInsight(SpeakerRole.FRIEND, List.of())
                ),
                List.of(commonInterest)
        );
    }
}
