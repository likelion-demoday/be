package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.resay.domain.analysis.support.AnalysisTestSpeakers.speakersFor;

class LinerAnalysisResponseGrounderTest {

    private final LinerAnalysisResponseGrounder grounder =
            new LinerAnalysisResponseGrounder();

    @Test
    void recalculatesFrequentExpressionCountAndEvidenceFromTranscript() {
        QualitativeAnalysis response = responseWithExpression(
                new QualitativeAnalysis.FrequentExpression(
                        QualitativeAnalysis.FrequentExpressionCategory.WORD,
                        "먼저",
                        99,
                        List.of(1L)
                )
        );

        QualitativeAnalysis grounded = grounder.ground(source(), response);

        QualitativeAnalysis.FrequentExpression expression = grounded.speakerInsights()
                .get(0)
                .frequentExpressions()
                .get(0);
        assertThat(expression.count()).isEqualTo(3);
        assertThat(expression.evidenceSegmentIds()).containsExactly(1L, 3L);
    }

    @Test
    void removesCandidateThatDoesNotActuallyRepeat() {
        QualitativeAnalysis response = responseWithExpression(
                new QualitativeAnalysis.FrequentExpression(
                        QualitativeAnalysis.FrequentExpressionCategory.WORD,
                        "연락",
                        2,
                        List.of(1L)
                )
        );

        QualitativeAnalysis grounded = grounder.ground(source(), response);

        assertThat(grounded.speakerInsights().get(0).frequentExpressions()).isEmpty();
        assertThat(grounded.speakerInsights().get(0).frequentExpressionSummary()).isNull();
    }

    @Test
    void removesSentenceLikeExpressionFromPublicCandidates() {
        QualitativeAnalysis response = responseWithExpression(
                new QualitativeAnalysis.FrequentExpression(
                        QualitativeAnalysis.FrequentExpressionCategory.SPEECH_HABIT,
                        "아 스타벅스 갔구나",
                        2,
                        List.of(1L, 3L)
                )
        );

        QualitativeAnalysis grounded = grounder.ground(source(), response);

        assertThat(grounded.speakerInsights().get(0).frequentExpressions()).isEmpty();
        assertThat(grounded.speakerInsights().get(0).frequentExpressionSummary()).isNull();
    }

    @Test
    void removesLowValuePronounFromPublicCandidates() {
        QualitativeAnalysis response = responseWithExpression(
                new QualitativeAnalysis.FrequentExpression(
                        QualitativeAnalysis.FrequentExpressionCategory.WORD,
                        "내가",
                        3,
                        List.of(1L, 3L)
                )
        );

        AnalysisSource source = new AnalysisSource(
                1L,
                AnalysisScenario.PARENT_CHILD_CONFLICT,
                10_000L,
                speakersFor(AnalysisScenario.PARENT_CHILD_CONFLICT),
                List.of(
                        new AnalysisSegment(1L, SpeakerRole.PARENT, 0L, 1_000L, "내가 먼저 연락할게."),
                        new AnalysisSegment(2L, SpeakerRole.CHILD, 1_100L, 2_000L, "알겠어."),
                        new AnalysisSegment(3L, SpeakerRole.PARENT, 2_100L, 3_000L, "내가 먼저 물어볼게.")
                )
        );

        QualitativeAnalysis grounded = grounder.ground(source, response);

        assertThat(grounded.speakerInsights().get(0).frequentExpressions()).isEmpty();
        assertThat(grounded.speakerInsights().get(0).frequentExpressionSummary()).isNull();
    }

    @Test
    void removesLowValueDependentNounFromPublicCandidates() {
        QualitativeAnalysis response = responseWithExpression(
                new QualitativeAnalysis.FrequentExpression(
                        QualitativeAnalysis.FrequentExpressionCategory.WORD,
                        "거",
                        3,
                        List.of(1L, 3L)
                )
        );

        AnalysisSource source = new AnalysisSource(
                1L,
                AnalysisScenario.PARENT_CHILD_CONFLICT,
                10_000L,
                speakersFor(AnalysisScenario.PARENT_CHILD_CONFLICT),
                List.of(
                        new AnalysisSegment(1L, SpeakerRole.PARENT, 0L, 1_000L, "그거 내가 한 거야."),
                        new AnalysisSegment(2L, SpeakerRole.CHILD, 1_100L, 2_000L, "알겠어."),
                        new AnalysisSegment(3L, SpeakerRole.PARENT, 2_100L, 3_000L, "다음 거는 같이 하자.")
                )
        );

        QualitativeAnalysis grounded = grounder.ground(source, response);

        assertThat(grounded.speakerInsights().get(0).frequentExpressions()).isEmpty();
        assertThat(grounded.speakerInsights().get(0).frequentExpressionSummary()).isNull();
    }

    @Test
    void replacesSummaryEvidenceWithGroundedExpressionEvidence() {
        QualitativeAnalysis base = responseWithExpression(
                new QualitativeAnalysis.FrequentExpression(
                        QualitativeAnalysis.FrequentExpressionCategory.WORD,
                        "먼저",
                        99,
                        List.of(1L)
                )
        );
        QualitativeAnalysis.SpeakerInsight originalInsight = base.speakerInsights().get(0);
        QualitativeAnalysis response = new QualitativeAnalysis(
                base.overview(),
                base.timeline(),
                base.topics(),
                base.characterInsights(),
                List.of(new QualitativeAnalysis.SpeakerInsight(
                        originalInsight.speakerRole(),
                        originalInsight.patterns(),
                        new QualitativeAnalysis.FrequentExpressionSummary(
                                "반복 표현을 사용해요",
                                "먼저라는 표현을 반복해서 사용해요.",
                                List.of(2L)
                        ),
                        originalInsight.frequentExpressions()
                )),
                base.interestInsights(),
                base.spicinessInsights(),
                base.reactionStyleInsights(),
                base.scenarioInsights()
        );

        QualitativeAnalysis grounded = grounder.ground(source(), response);

        assertThat(grounded.speakerInsights().get(0)
                .frequentExpressionSummary()
                .evidenceSegmentIds())
                .containsExactly(1L, 3L);
    }

    @Test
    void removesSegmentAssignmentsDuplicatedAcrossTopics() {
        QualitativeAnalysis base = responseWithExpression(
                new QualitativeAnalysis.FrequentExpression(
                        QualitativeAnalysis.FrequentExpressionCategory.WORD,
                        "먼저",
                        3,
                        List.of(1L, 3L)
                )
        );
        QualitativeAnalysis response = new QualitativeAnalysis(
                base.overview(),
                base.timeline(),
                List.of(
                        new QualitativeAnalysis.Topic("연락", "연락을 조율했어요.", List.of(1L, 2L)),
                        new QualitativeAnalysis.Topic("약속", "약속을 정했어요.", List.of(2L, 3L))
                ),
                base.characterInsights(),
                base.speakerInsights(),
                base.interestInsights(),
                base.spicinessInsights(),
                base.reactionStyleInsights(),
                base.scenarioInsights()
        );

        QualitativeAnalysis grounded = grounder.ground(source(), response);

        assertThat(grounded.topics().get(0).segmentIds()).containsExactly(1L, 2L);
        assertThat(grounded.topics().get(1).segmentIds()).containsExactly(3L);
    }

    @Test
    void recalculatesSwearWordCountAndEvidenceFromTranscript() {
        QualitativeAnalysis response = responseWithSwearWord(
                new QualitativeAnalysis.SwearWordUsage("미친", 99, List.of(1L))
        );

        QualitativeAnalysis grounded = grounder.ground(sourceWithSwearWords(), response);

        QualitativeAnalysis.SwearWordUsage swearWord = grounded.spicinessInsights()
                .get(0)
                .swearWords()
                .get(0);
        assertThat(swearWord.count()).isEqualTo(3);
        assertThat(swearWord.evidenceSegmentIds()).containsExactly(1L, 3L);
    }

    @Test
    void removesSwearWordCandidateThatIsNotInTranscript() {
        QualitativeAnalysis response = responseWithSwearWord(
                new QualitativeAnalysis.SwearWordUsage("없는표현", 2, List.of(1L))
        );

        QualitativeAnalysis grounded = grounder.ground(sourceWithSwearWords(), response);

        assertThat(grounded.spicinessInsights().get(0).swearWords()).isEmpty();
        assertThat(grounded.spicinessInsights().get(0).observations()).isEmpty();
    }

    @Test
    void keepsConservativeContextualCorrectionForRepresentativeEvidence() {
        AnalysisSource source = evidenceSource();
        QualitativeAnalysis response = responseWithRepresentativeEvidence(
                "“아니, 그거 진짜 힘들었을 것 같아.”"
        );

        QualitativeAnalysis grounded = grounder.ground(source, response);

        assertThat(grounded.representativeEvidences().get(0).displayContent())
                .isEqualTo("아니, 그거 진짜 힘들었을 것 같아.");
    }

    @Test
    void fallsBackToOriginalWhenCorrectionChangesMeaningTooMuch() {
        AnalysisSource source = evidenceSource();
        QualitativeAnalysis response = responseWithRepresentativeEvidence(
                "오늘은 대화를 그만하고 여행을 떠나자."
        );

        QualitativeAnalysis grounded = grounder.ground(source, response);

        assertThat(grounded.representativeEvidences().get(0).displayContent())
                .isEqualTo("아니 그거 진짜 힘들었을거 가타");
    }

    @Test
    void replacesDailyRepresentativeEvidenceNotLinkedToReactionExample() {
        AnalysisSource source = new AnalysisSource(
                4L,
                AnalysisScenario.FRIEND_DAILY,
                10_000L,
                speakersFor(AnalysisScenario.FRIEND_DAILY),
                List.of(
                        new AnalysisSegment(1L, SpeakerRole.SELF, 0L, 1_000L, "오늘 뭐 했어?"),
                        new AnalysisSegment(2L, SpeakerRole.FRIEND, 1_100L, 2_000L, "학교 갔다 왔어."),
                        new AnalysisSegment(3L, SpeakerRole.SELF, 2_100L, 3_000L, "재밌었어?")
                )
        );
        QualitativeAnalysis response = new QualitativeAnalysis(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(
                        reactionStyle(SpeakerRole.SELF, 1L),
                        reactionStyle(SpeakerRole.FRIEND, 2L)
                ),
                null,
                List.of(
                        representativeReaction(SpeakerRole.SELF, 3L, "재밌었어?"),
                        representativeReaction(SpeakerRole.SELF, 1L, "오늘은 뭐 했어?"),
                        representativeReaction(SpeakerRole.FRIEND, 2L, "학교 갔다 왔어.")
                )
        );

        QualitativeAnalysis grounded = grounder.ground(source, response);

        assertThat(grounded.representativeEvidences())
                .extracting(
                        QualitativeAnalysis.RepresentativeEvidence::speakerRole,
                        QualitativeAnalysis.RepresentativeEvidence::segmentId,
                        QualitativeAnalysis.RepresentativeEvidence::displayContent
                )
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                SpeakerRole.SELF, 1L, "오늘은 뭐 했어?"
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                SpeakerRole.FRIEND, 2L, "학교 갔다 왔어."
                        )
                );
    }

    @Test
    void clearsReactionCategoryFromConflictRepresentativeEvidence() {
        QualitativeAnalysis response = new QualitativeAnalysis(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new QualitativeAnalysis.RepresentativeEvidence(
                        QualitativeAnalysis.ReportEvidenceType.WORRY_START,
                        SpeakerRole.PARENT,
                        QualitativeAnalysis.ReactionCategory.THINKING,
                        1L,
                        "먼저 연락해. 먼저 알려줘."
                ))
        );

        QualitativeAnalysis grounded = grounder.ground(source(), response);

        assertThat(grounded.representativeEvidences().get(0).reactionCategory()).isNull();
    }

    private AnalysisSource source() {
        return new AnalysisSource(
                1L,
                AnalysisScenario.PARENT_CHILD_CONFLICT,
                10_000L,
                speakersFor(AnalysisScenario.PARENT_CHILD_CONFLICT),
                List.of(
                        new AnalysisSegment(
                                1L,
                                SpeakerRole.PARENT,
                                0L,
                                1_000L,
                                "먼저 연락해. 먼저 알려줘."
                        ),
                        new AnalysisSegment(
                                2L,
                                SpeakerRole.CHILD,
                                1_100L,
                                2_000L,
                                "알겠어."
                        ),
                        new AnalysisSegment(
                                3L,
                                SpeakerRole.PARENT,
                                2_100L,
                                3_000L,
                                "다음에는 먼저 물어볼게."
                        )
                )
        );
    }

    private QualitativeAnalysis responseWithExpression(
            QualitativeAnalysis.FrequentExpression expression
    ) {
        QualitativeAnalysis.SpeakerInsight insight =
                new QualitativeAnalysis.SpeakerInsight(
                        SpeakerRole.PARENT,
                        List.of(),
                        new QualitativeAnalysis.FrequentExpressionSummary(
                                "반복 표현을 사용해요",
                                "먼저라는 표현을 반복해서 사용했어요.",
                                List.of(1L, 3L)
                        ),
                        List.of(expression)
                );
        return new QualitativeAnalysis(
                null,
                null,
                null,
                null,
                List.of(insight),
                null,
                null,
                null,
                null
        );
    }

    private AnalysisSource sourceWithSwearWords() {
        return new AnalysisSource(
                2L,
                AnalysisScenario.FRIEND_DAILY,
                10_000L,
                speakersFor(AnalysisScenario.FRIEND_DAILY),
                List.of(
                        new AnalysisSegment(1L, SpeakerRole.SELF, 0L, 1_000L, "미친 진짜 미친 일이네"),
                        new AnalysisSegment(2L, SpeakerRole.FRIEND, 1_100L, 2_000L, "그러게 놀랐어"),
                        new AnalysisSegment(3L, SpeakerRole.SELF, 2_100L, 3_000L, "미친 너무 웃기다")
                )
        );
    }

    private AnalysisSource evidenceSource() {
        return new AnalysisSource(
                3L,
                AnalysisScenario.FRIEND_DAILY,
                10_000L,
                speakersFor(AnalysisScenario.FRIEND_DAILY),
                List.of(
                        new AnalysisSegment(
                                1L,
                                SpeakerRole.SELF,
                                0L,
                                1_000L,
                                "아니 그거 진짜 힘들었을거 가타"
                        ),
                        new AnalysisSegment(
                                2L,
                                SpeakerRole.FRIEND,
                                1_100L,
                                2_000L,
                                "응, 정말 힘들었어."
                        )
                )
        );
    }

    private QualitativeAnalysis responseWithRepresentativeEvidence(String displayContent) {
        return new QualitativeAnalysis(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new QualitativeAnalysis.RepresentativeEvidence(
                        QualitativeAnalysis.ReportEvidenceType.REACTION_STYLE_EXAMPLE,
                        SpeakerRole.SELF,
                        QualitativeAnalysis.ReactionCategory.FEELING,
                        1L,
                        displayContent
                ))
        );
    }

    private QualitativeAnalysis.ReactionStyleInsight reactionStyle(
            SpeakerRole role,
            Long evidenceId
    ) {
        return new QualitativeAnalysis.ReactionStyleInsight(
                role,
                50,
                50,
                "정보와 감정에 함께 반응했어요.",
                List.of(new QualitativeAnalysis.ReactionExample(
                        QualitativeAnalysis.ReactionCategory.MIXED,
                        "혼합 반응",
                        "정보와 감정에 함께 반응했어요.",
                        List.of(evidenceId)
                ))
        );
    }

    private QualitativeAnalysis.RepresentativeEvidence representativeReaction(
            SpeakerRole role,
            Long segmentId,
            String displayContent
    ) {
        return new QualitativeAnalysis.RepresentativeEvidence(
                QualitativeAnalysis.ReportEvidenceType.REACTION_STYLE_EXAMPLE,
                role,
                QualitativeAnalysis.ReactionCategory.MIXED,
                segmentId,
                displayContent
        );
    }

    private QualitativeAnalysis responseWithSwearWord(
            QualitativeAnalysis.SwearWordUsage swearWord
    ) {
        return new QualitativeAnalysis(
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new QualitativeAnalysis.SpicinessInsight(
                        SpeakerRole.SELF,
                        30,
                        "강한 표현을 사용했어요.",
                        List.of(swearWord),
                        List.of(new QualitativeAnalysis.SpicinessObservation(
                                QualitativeAnalysis.SpicinessCategory.SWEAR_WORD,
                                "비속어 사용",
                                "강한 표현을 사용했어요.",
                                List.of(1L)
                        ))
                )),
                null,
                null
        );
    }
}
