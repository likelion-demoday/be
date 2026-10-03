package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LinerAnalysisQualityEvaluatorTest {

    private final LinerAnalysisQualityEvaluator evaluator = new LinerAnalysisQualityEvaluator();

    @Test
    void comparesExpectedObservationsWithActualAnalysis() {
        LinerAnalysisQualityEvaluation result = evaluator.evaluate(
                spec(),
                analysis(),
                LinerAnalysisQualityEvaluation.TranscriptVariant.NOISY
        );

        assertThat(result.observations()).hasSize(2);
        assertThat(result.observations().get(0).found()).isTrue();
        assertThat(result.observations().get(0).overlappingEvidenceSegmentIds())
                .containsExactly(3L);
        assertThat(result.observations().get(1).found()).isFalse();
        assertThat(result.detectedForbiddenClaims())
                .containsExactly("관계에 심각한 갈등이 있다");
        assertThat(result.referencedCorruptedSegmentIds()).containsExactly(3L, 7L);
        assertThat(result.referencedMeaningRiskSegmentIds()).containsExactly(7L);
    }

    @Test
    void doesNotMarkCorrectedTranscriptEvidenceAsCorrupted() {
        LinerAnalysisQualityEvaluation result = evaluator.evaluate(
                spec(),
                analysis(),
                LinerAnalysisQualityEvaluation.TranscriptVariant.CORRECTED
        );

        assertThat(result.referencedCorruptedSegmentIds()).isEmpty();
        assertThat(result.referencedMeaningRiskSegmentIds()).isEmpty();
    }

    private LinerAnalysisEvaluationSpec spec() {
        return new LinerAnalysisEvaluationSpec(
                AnalysisScenario.FRIEND_DAILY,
                "MILD_WITH_SEMANTIC_ERRORS",
                List.of(3L, 7L),
                List.of(7L),
                List.of("회사 적응"),
                List.of(
                        new LinerAnalysisEvaluationSpec.ExpectedObservation(
                                "FOLLOW_UP_QUESTION",
                                SpeakerRole.SELF,
                                List.of(1L, 3L)
                        ),
                        new LinerAnalysisEvaluationSpec.ExpectedObservation(
                                "PLAYFUL_EXCHANGE",
                                null,
                                List.of(4L, 5L)
                        )
                ),
                List.of("관계에 심각한 갈등이 있다")
        );
    }

    private QualitativeAnalysis analysis() {
        return new QualitativeAnalysis(
                new QualitativeAnalysis.Overview(
                        "대화 요약",
                        "두 사람의 관계에 심각한 갈등이 있다.",
                        List.of(1L)
                ),
                List.of(new QualitativeAnalysis.TimelineItem(
                        "회사 이야기",
                        "새 회사 적응에 관해 이야기했습니다.",
                        List.of(1L, 7L)
                )),
                List.of(
                        new QualitativeAnalysis.SpeakerInsight(
                                SpeakerRole.SELF,
                                List.of(new QualitativeAnalysis.SpeakerPattern(
                                        QualitativeAnalysis.SpeakerPatternCategory.FOLLOW_UP_QUESTION,
                                        "후속 질문",
                                        "상대의 설명을 듣고 질문했습니다.",
                                        List.of(3L)
                                ))
                        ),
                        new QualitativeAnalysis.SpeakerInsight(SpeakerRole.FRIEND, List.of())
                ),
                List.of()
        );
    }
}
