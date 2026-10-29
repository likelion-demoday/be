package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static com.example.resay.domain.analysis.support.AnalysisTestSpeakers.speakersFor;

class LinerAnalysisResponseValidatorTest {

    private final LinerAnalysisResponseValidator validator = new LinerAnalysisResponseValidator();

    @Test
    void acceptsResponseGroundedInSourceSegments() {
        validator.validate(source(), validResponse());
    }

    @Test
    void acceptsCharacterCardTextWithoutLengthMutation() {
        QualitativeAnalysis valid = validResponse();
        String name = "열두자를넘어가는캐릭터이름";
        String description =
                "이번 대화에서 상대의 말을 끝까지 듣고 새로운 질문을 계속 덧붙이며 대화를 자연스럽게 이어 가는 모습이 보여요.";
        QualitativeAnalysis response = withCharacterInsights(
                valid,
                List.of(
                        new QualitativeAnalysis.CharacterInsight(
                                SpeakerRole.SELF,
                                name,
                                description,
                                List.of(1L)
                        ),
                        character(SpeakerRole.FRIEND, 2L)
                )
        );

        assertThatCode(() -> validator.validate(source(), response)).doesNotThrowAnyException();

        QualitativeAnalysis grounded = new LinerAnalysisResponseGrounder()
                .ground(source(), response);

        assertThatCode(() -> validator.validate(source(), grounded)).doesNotThrowAnyException();
        assertThat(grounded.characterInsights().get(0).name())
                .isEqualTo(name);
        assertThat(grounded.characterInsights().get(0).description())
                .isEqualTo(description);
    }

    @Test
    void rejectsUnknownEvidenceSegmentId() {
        QualitativeAnalysis valid = validResponse();
        QualitativeAnalysis response = copy(
                valid,
                new QualitativeAnalysis.Overview("요약", "대화 요약", List.of(999L)),
                valid.timeline(),
                valid.speakerInsights(),
                valid.scenarioInsights()
        );

        assertThatThrownBy(() -> validator.validate(source(), response))
                .isInstanceOfSatisfying(LinerAnalysisException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo("invalid_analysis_response")
                );
    }

    @Test
    void rejectsSpeakerRolesOutsideScenario() {
        QualitativeAnalysis valid = validResponse();
        List<QualitativeAnalysis.SpeakerInsight> invalidSpeakers = List.of(
                speakerInsight(SpeakerRole.SELF, 1L, List.of()),
                speakerInsight(SpeakerRole.PARTNER, 2L, List.of())
        );
        QualitativeAnalysis response = copy(
                valid,
                valid.overview(),
                valid.timeline(),
                invalidSpeakers,
                valid.scenarioInsights()
        );

        assertThatThrownBy(() -> validator.validate(source(), response))
                .isInstanceOf(LinerAnalysisException.class)
                .hasMessageContaining("시나리오와 일치하지 않습니다");
    }

    @Test
    void rejectsTimelineOutsideChronologicalOrder() {
        QualitativeAnalysis valid = validResponse();
        QualitativeAnalysis response = copy(
                valid,
                valid.overview(),
                List.of(
                        new QualitativeAnalysis.TimelineItem("두 번째", "두 번째 주제", List.of(3L)),
                        new QualitativeAnalysis.TimelineItem("첫 번째", "첫 번째 주제", List.of(1L))
                ),
                valid.speakerInsights(),
                valid.scenarioInsights()
        );

        assertThatThrownBy(() -> validator.validate(source(), response))
                .isInstanceOf(LinerAnalysisException.class)
                .hasMessageContaining("시간순");
    }

    @Test
    void rejectsSegmentAssignedToMultipleTopics() {
        QualitativeAnalysis valid = validResponse();
        QualitativeAnalysis response = new QualitativeAnalysis(
                valid.overview(),
                valid.timeline(),
                List.of(
                        new QualitativeAnalysis.Topic(
                                "학교 근황",
                                "학교에서 있었던 일을 이야기했어요.",
                                List.of(1L, 2L)
                        ),
                        new QualitativeAnalysis.Topic(
                                "질문과 응답",
                                "질문을 주고받았어요.",
                                List.of(2L, 3L)
                        )
                ),
                valid.characterInsights(),
                valid.speakerInsights(),
                valid.interestInsights(),
                valid.spicinessInsights(),
                valid.reactionStyleInsights(),
                valid.scenarioInsights()
        );

        assertThatThrownBy(() -> validator.validate(source(), response))
                .isInstanceOf(LinerAnalysisException.class)
                .hasMessageContaining("하나의 주제에만");
    }

    @Test
    void rejectsPatternWithoutEvidenceFromAttributedSpeaker() {
        QualitativeAnalysis valid = validResponse();
        QualitativeAnalysis.SpeakerPattern invalidPattern =
                new QualitativeAnalysis.SpeakerPattern(
                        QualitativeAnalysis.SpeakerPatternCategory.FOLLOW_UP_QUESTION,
                        "후속 질문",
                        "질문으로 대화를 이어갔어요.",
                        List.of(2L)
                );
        List<QualitativeAnalysis.SpeakerInsight> invalidSpeakers = List.of(
                speakerInsight(SpeakerRole.SELF, 1L, List.of(invalidPattern)),
                speakerInsight(SpeakerRole.FRIEND, 2L, List.of())
        );
        QualitativeAnalysis response = copy(
                valid,
                valid.overview(),
                valid.timeline(),
                invalidSpeakers,
                valid.scenarioInsights()
        );

        assertThatThrownBy(() -> validator.validate(source(), response))
                .isInstanceOf(LinerAnalysisException.class)
                .hasMessageContaining("해당 화자의 근거 발화");
    }

    @Test
    void rejectsScenarioInsightCategoryOutsideScenario() {
        QualitativeAnalysis valid = validResponse();
        QualitativeAnalysis.ScenarioInsight conflictInsight =
                new QualitativeAnalysis.ScenarioInsight(
                        QualitativeAnalysis.ScenarioInsightCategory.CONFLICT_TOPIC,
                        List.of(SpeakerRole.SELF, SpeakerRole.FRIEND),
                        "갈등 주제",
                        "의견 차이가 나타났어요.",
                        List.of(1L, 2L)
                );
        QualitativeAnalysis response = copy(
                valid,
                valid.overview(),
                valid.timeline(),
                valid.speakerInsights(),
                List.of(conflictInsight)
        );

        assertThatThrownBy(() -> validator.validate(source(), response))
                .isInstanceOf(LinerAnalysisException.class)
                .hasMessageContaining("시나리오에 맞지 않는 상황별 관찰");
    }

    @Test
    void rejectsReactionPercentagesThatDoNotSumToOneHundred() {
        QualitativeAnalysis valid = validResponse();
        QualitativeAnalysis response = new QualitativeAnalysis(
                valid.overview(),
                valid.timeline(),
                valid.topics(),
                valid.characterInsights(),
                valid.speakerInsights(),
                valid.interestInsights(),
                valid.spicinessInsights(),
                List.of(
                        reaction(SpeakerRole.SELF, 1L, 60, 60),
                        reaction(SpeakerRole.FRIEND, 2L, 50, 50)
                ),
                valid.scenarioInsights()
        );

        assertThatThrownBy(() -> validator.validate(source(), response))
                .isInstanceOf(LinerAnalysisException.class)
                .hasMessageContaining("합은 100");
    }

    @Test
    void rejectsSwearWordCountThatDoesNotMatchTranscript() {
        QualitativeAnalysis valid = validResponse();
        QualitativeAnalysis response = new QualitativeAnalysis(
                valid.overview(),
                valid.timeline(),
                valid.topics(),
                valid.characterInsights(),
                valid.speakerInsights(),
                valid.interestInsights(),
                List.of(
                        new QualitativeAnalysis.SpicinessInsight(
                                SpeakerRole.SELF,
                                30,
                                "비속어가 나타났어요.",
                                List.of(new QualitativeAnalysis.SwearWordUsage(
                                        "미친",
                                        99,
                                        List.of(1L)
                                )),
                                List.of(new QualitativeAnalysis.SpicinessObservation(
                                        QualitativeAnalysis.SpicinessCategory.SWEAR_WORD,
                                        "비속어 사용",
                                        "강한 표현을 사용했어요.",
                                        List.of(1L)
                                ))
                        ),
                        spiciness(SpeakerRole.FRIEND)
                ),
                valid.reactionStyleInsights(),
                valid.scenarioInsights()
        );

        assertThatThrownBy(() -> validator.validate(source(), response))
                .isInstanceOf(LinerAnalysisException.class)
                .hasMessageContaining("등장 횟수와 일치하지 않습니다");
    }

    @Test
    void rejectsFrequentExpressionMissingFromEvidence() {
        QualitativeAnalysis valid = validResponse();
        QualitativeAnalysis.SpeakerInsight invalidSelf = new QualitativeAnalysis.SpeakerInsight(
                SpeakerRole.SELF,
                List.of(),
                new QualitativeAnalysis.FrequentExpressionSummary(
                        "반복 표현을 사용해요",
                        "같은 표현을 반복해서 사용했어요.",
                        List.of(1L)
                ),
                List.of(new QualitativeAnalysis.FrequentExpression(
                        QualitativeAnalysis.FrequentExpressionCategory.WORD,
                        "없는 표현",
                        3,
                        List.of(1L)
                ))
        );
        QualitativeAnalysis response = copy(
                valid,
                valid.overview(),
                valid.timeline(),
                List.of(invalidSelf, speakerInsight(SpeakerRole.FRIEND, 2L, List.of())),
                valid.scenarioInsights()
        );

        assertThatThrownBy(() -> validator.validate(source(), response))
                .isInstanceOf(LinerAnalysisException.class)
                .hasMessageContaining("실제로 존재하지 않습니다");
    }

    @Test
    void rejectsFrequentExpressionThatAppearsOnlyOnce() {
        QualitativeAnalysis valid = validResponse();
        QualitativeAnalysis.SpeakerInsight invalidSelf = new QualitativeAnalysis.SpeakerInsight(
                SpeakerRole.SELF,
                List.of(),
                new QualitativeAnalysis.FrequentExpressionSummary(
                        "반복 표현을 사용해요",
                        "같은 표현을 반복해서 사용했어요.",
                        List.of(1L)
                ),
                List.of(new QualitativeAnalysis.FrequentExpression(
                        QualitativeAnalysis.FrequentExpressionCategory.WORD,
                        "오늘",
                        1,
                        List.of(1L)
                ))
        );
        QualitativeAnalysis response = copy(
                valid,
                valid.overview(),
                valid.timeline(),
                List.of(invalidSelf, speakerInsight(SpeakerRole.FRIEND, 2L, List.of())),
                valid.scenarioInsights()
        );

        assertThatThrownBy(() -> validator.validate(source(), response))
                .isInstanceOf(LinerAnalysisException.class)
                .hasMessageContaining("두 번 이상");
    }

    @Test
    void acceptsConflictPositionsSeparatedBySpeaker() {
        validator.validate(conflictSource(), conflictResponse(validConflictInsights()));
    }

    @Test
    void rejectsConflictPositionsCombinedIntoOneInsight() {
        List<QualitativeAnalysis.ScenarioInsight> insights = List.of(
                scenarioInsight(
                        QualitativeAnalysis.ScenarioInsightCategory.CONFLICT_TOPIC,
                        List.of(SpeakerRole.SELF, SpeakerRole.PARTNER),
                        List.of(1L, 2L)
                ),
                scenarioInsight(
                        QualitativeAnalysis.ScenarioInsightCategory.CONFLICT_POSITION,
                        List.of(SpeakerRole.SELF, SpeakerRole.PARTNER),
                        List.of(1L, 2L)
                )
        );

        assertThatThrownBy(() -> validator.validate(
                conflictSource(),
                conflictResponse(insights)
        ))
                .isInstanceOf(LinerAnalysisException.class)
                .hasMessageContaining("화자별로 분리");
    }

    @Test
    void rejectsConflictAnalysisWithoutConflictTopic() {
        List<QualitativeAnalysis.ScenarioInsight> positions = validConflictInsights().stream()
                .filter(insight -> insight.category()
                        == QualitativeAnalysis.ScenarioInsightCategory.CONFLICT_POSITION)
                .toList();

        assertThatThrownBy(() -> validator.validate(
                conflictSource(),
                conflictResponse(positions)
        ))
                .isInstanceOf(LinerAnalysisException.class)
                .hasMessageContaining("CONFLICT_TOPIC");
    }

    @Test
    void acceptsDailyRepresentativeEvidenceLinkedToReactionExamples() {
        QualitativeAnalysis response = withRepresentativeEvidences(
                validResponse(),
                List.of(
                        representativeReaction(SpeakerRole.SELF, 1L, "오늘 뭐 했어? 미친 미친"),
                        representativeReaction(SpeakerRole.FRIEND, 2L, "학교 갔다 왔어")
                )
        );

        validator.validate(source(), response);
    }

    @Test
    void acceptsDailyResponseAfterMismatchedRepresentativeEvidenceIsRecovered() {
        QualitativeAnalysis response = withRepresentativeEvidences(
                validResponse(),
                List.of(
                        representativeReaction(SpeakerRole.SELF, 3L, "재밌었어?"),
                        representativeReaction(SpeakerRole.FRIEND, 2L, "학교 갔다 왔어")
                )
        );

        QualitativeAnalysis grounded = new LinerAnalysisResponseGrounder()
                .ground(source(), response);

        assertThat(grounded.representativeEvidences())
                .filteredOn(evidence -> evidence.speakerRole() == SpeakerRole.SELF)
                .extracting(QualitativeAnalysis.RepresentativeEvidence::segmentId)
                .containsExactly(1L);
        validator.validate(source(), grounded);
    }

    @Test
    void rejectsRepresentativeEvidenceWithMismatchedSpeaker() {
        QualitativeAnalysis response = withRepresentativeEvidences(
                validResponse(),
                List.of(
                        representativeReaction(SpeakerRole.SELF, 2L, "학교 갔다 왔어"),
                        representativeReaction(SpeakerRole.FRIEND, 2L, "학교 갔다 왔어")
                )
        );

        assertThatThrownBy(() -> validator.validate(source(), response))
                .isInstanceOf(LinerAnalysisException.class)
                .hasMessageContaining("화자가 원본 발화와 일치하지 않습니다");
    }

    @Test
    void acceptsStructuredReportMetricsAndRelationshipSummary() {
        QualitativeAnalysis valid = validResponse();
        QualitativeAnalysis response = withExtendedFields(
                valid,
                List.of(new QualitativeAnalysis.ReportMetric(
                        QualitativeAnalysis.ReportMetricType.ENCOURAGEMENT,
                        SpeakerRole.SELF,
                        2,
                        QualitativeAnalysis.ReportMetricUnit.COUNT,
                        "응원 표현",
                        "상대의 이야기를 이어 주는 표현이 두 발화에서 보여요.",
                        List.of(1L, 3L)
                ))
        );

        assertThatCode(() -> validator.validate(source(), response)).doesNotThrowAnyException();
    }

    @Test
    void rejectsPercentMetricOverOneHundred() {
        QualitativeAnalysis valid = validResponse();
        QualitativeAnalysis response = withExtendedFields(
                valid,
                List.of(new QualitativeAnalysis.ReportMetric(
                        QualitativeAnalysis.ReportMetricType.DIRECT_AFFECTION,
                        SpeakerRole.SELF,
                        101,
                        QualitativeAnalysis.ReportMetricUnit.PERCENT,
                        "직접 호감 표현",
                        "이번 대화에서 직접적인 호감 표현이 보여요.",
                        List.of(1L)
                ))
        );

        assertThatThrownBy(() -> validator.validate(source(), response))
                .isInstanceOf(LinerAnalysisException.class)
                .hasMessageContaining("100 이하");
    }

    private QualitativeAnalysis withExtendedFields(
            QualitativeAnalysis source,
            List<QualitativeAnalysis.ReportMetric> metrics
    ) {
        return new QualitativeAnalysis(
                source.overview(),
                source.timeline(),
                source.topics(),
                source.characterInsights(),
                source.speakerInsights(),
                source.interestInsights(),
                source.spicinessInsights(),
                source.reactionStyleInsights(),
                source.scenarioInsights(),
                source.representativeEvidences(),
                new QualitativeAnalysis.RelationshipSummary(
                        "질문하는 사람 × 답해 주는 사람",
                        "질문과 답변을 주고받으며 대화를 이어가요.",
                        List.of(1L, 2L)
                ),
                metrics,
                List.of(),
                List.of()
        );
    }

    private QualitativeAnalysis withCharacterInsights(
            QualitativeAnalysis source,
            List<QualitativeAnalysis.CharacterInsight> characterInsights
    ) {
        return new QualitativeAnalysis(
                source.overview(),
                source.timeline(),
                source.topics(),
                characterInsights,
                source.speakerInsights(),
                source.interestInsights(),
                source.spicinessInsights(),
                source.reactionStyleInsights(),
                source.scenarioInsights(),
                source.representativeEvidences(),
                source.relationshipSummary(),
                source.reportMetrics(),
                source.speakerTendencies(),
                source.coachingSuggestions()
        );
    }

    private AnalysisSource source() {
        return new AnalysisSource(
                1L,
                AnalysisScenario.FRIEND_DAILY,
                10_000L,
                speakersFor(AnalysisScenario.FRIEND_DAILY),
                List.of(
                        new AnalysisSegment(1L, SpeakerRole.SELF, 100L, 500L, "오늘 뭐 했어? 미친 미친"),
                        new AnalysisSegment(2L, SpeakerRole.FRIEND, 600L, 900L, "학교 갔다 왔어"),
                        new AnalysisSegment(3L, SpeakerRole.SELF, 1_000L, 1_400L, "재밌었어?")
                )
        );
    }

    private AnalysisSource conflictSource() {
        return new AnalysisSource(
                2L,
                AnalysisScenario.COUPLE_CONFLICT,
                10_000L,
                speakersFor(AnalysisScenario.COUPLE_CONFLICT),
                List.of(
                        new AnalysisSegment(1L, SpeakerRole.SELF, 100L, 500L, "왜 연락 안 했어?"),
                        new AnalysisSegment(2L, SpeakerRole.PARTNER, 600L, 900L, "회의 중이었어")
                )
        );
    }

    private QualitativeAnalysis conflictResponse(
            List<QualitativeAnalysis.ScenarioInsight> scenarioInsights
    ) {
        return new QualitativeAnalysis(
                new QualitativeAnalysis.Overview("연락 갈등", "연락 문제를 이야기했어요.", List.of(1L, 2L)),
                List.of(new QualitativeAnalysis.TimelineItem(
                        "갈등 확인",
                        "서로의 입장을 설명했어요.",
                        List.of(1L, 2L)
                )),
                List.of(new QualitativeAnalysis.Topic(
                        "연락 문제",
                        "연락 여부를 이야기했어요.",
                        List.of(1L, 2L)
                )),
                List.of(
                        character(SpeakerRole.SELF, 1L),
                        character(SpeakerRole.PARTNER, 2L)
                ),
                List.of(
                        speakerInsight(SpeakerRole.SELF, 1L, List.of()),
                        speakerInsight(SpeakerRole.PARTNER, 2L, List.of())
                ),
                List.of(
                        interest(SpeakerRole.SELF, 1L),
                        interest(SpeakerRole.PARTNER, 2L)
                ),
                List.of(
                        spiciness(SpeakerRole.SELF),
                        spiciness(SpeakerRole.PARTNER)
                ),
                List.of(
                        reaction(SpeakerRole.SELF, 1L, 50, 50),
                        reaction(SpeakerRole.PARTNER, 2L, 50, 50)
                ),
                scenarioInsights
        );
    }

    private List<QualitativeAnalysis.ScenarioInsight> validConflictInsights() {
        return List.of(
                scenarioInsight(
                        QualitativeAnalysis.ScenarioInsightCategory.CONFLICT_TOPIC,
                        List.of(SpeakerRole.SELF, SpeakerRole.PARTNER),
                        List.of(1L, 2L)
                ),
                scenarioInsight(
                        QualitativeAnalysis.ScenarioInsightCategory.CONFLICT_POSITION,
                        List.of(SpeakerRole.SELF),
                        List.of(1L)
                ),
                scenarioInsight(
                        QualitativeAnalysis.ScenarioInsightCategory.CONFLICT_POSITION,
                        List.of(SpeakerRole.PARTNER),
                        List.of(2L)
                )
        );
    }

    private QualitativeAnalysis.ScenarioInsight scenarioInsight(
            QualitativeAnalysis.ScenarioInsightCategory category,
            List<SpeakerRole> roles,
            List<Long> evidenceIds
    ) {
        return new QualitativeAnalysis.ScenarioInsight(
                category,
                roles,
                "갈등 분석",
                "대화에서 관찰된 갈등 내용입니다.",
                evidenceIds
        );
    }

    private QualitativeAnalysis validResponse() {
        QualitativeAnalysis.SpeakerPattern questionPattern =
                new QualitativeAnalysis.SpeakerPattern(
                        QualitativeAnalysis.SpeakerPatternCategory.FOLLOW_UP_QUESTION,
                        "질문으로 이어가기",
                        "상대의 경험을 후속 질문으로 확인했어요.",
                        List.of(1L, 3L)
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
                List.of(new QualitativeAnalysis.Topic(
                        "학교 근황",
                        "학교에서 있었던 일을 이야기했어요.",
                        List.of(1L, 2L, 3L)
                )),
                List.of(
                        character(SpeakerRole.SELF, 1L),
                        character(SpeakerRole.FRIEND, 2L)
                ),
                List.of(
                        speakerInsight(SpeakerRole.SELF, 1L, List.of(questionPattern)),
                        speakerInsight(SpeakerRole.FRIEND, 2L, List.of())
                ),
                List.of(
                        interest(SpeakerRole.SELF, 1L),
                        interest(SpeakerRole.FRIEND, 2L)
                ),
                List.of(
                        spicinessWithSwearWords(),
                        spiciness(SpeakerRole.FRIEND)
                ),
                List.of(
                        reaction(SpeakerRole.SELF, 1L, 50, 50),
                        reaction(SpeakerRole.FRIEND, 2L, 50, 50)
                ),
                List.of(commonInterest)
        );
    }

    private QualitativeAnalysis withRepresentativeEvidences(
            QualitativeAnalysis source,
            List<QualitativeAnalysis.RepresentativeEvidence> evidences
    ) {
        return new QualitativeAnalysis(
                source.overview(),
                source.timeline(),
                source.topics(),
                source.characterInsights(),
                source.speakerInsights(),
                source.interestInsights(),
                source.spicinessInsights(),
                source.reactionStyleInsights(),
                source.scenarioInsights(),
                evidences
        );
    }

    private QualitativeAnalysis.RepresentativeEvidence representativeReaction(
            SpeakerRole speakerRole,
            Long segmentId,
            String displayContent
    ) {
        return new QualitativeAnalysis.RepresentativeEvidence(
                QualitativeAnalysis.ReportEvidenceType.REACTION_STYLE_EXAMPLE,
                speakerRole,
                QualitativeAnalysis.ReactionCategory.MIXED,
                segmentId,
                displayContent
        );
    }

    private QualitativeAnalysis.CharacterInsight character(SpeakerRole role, Long evidenceId) {
        return new QualitativeAnalysis.CharacterInsight(
                role,
                "대화 참여형",
                "이번 대화에서 상대의 말에 반응했어요.",
                List.of(evidenceId)
        );
    }

    private QualitativeAnalysis.SpeakerInsight speakerInsight(
            SpeakerRole role,
            Long evidenceId,
            List<QualitativeAnalysis.SpeakerPattern> patterns
    ) {
        return new QualitativeAnalysis.SpeakerInsight(
                role,
                patterns,
                null,
                List.of()
        );
    }

    private QualitativeAnalysis.InterestInsight interest(SpeakerRole role, Long evidenceId) {
        return new QualitativeAnalysis.InterestInsight(
                role,
                50,
                "이번 대화에서 질문으로 관심을 표현했어요.",
                List.of(new QualitativeAnalysis.InterestObservation(
                        QualitativeAnalysis.InterestCategory.QUESTION,
                        "질문하기",
                        "상대에게 질문했어요.",
                        List.of(evidenceId)
                ))
        );
    }

    private QualitativeAnalysis.SpicinessInsight spiciness(SpeakerRole role) {
        return new QualitativeAnalysis.SpicinessInsight(
                role,
                0,
                "강한 표현이 관찰되지 않았어요.",
                List.of(),
                List.of()
        );
    }

    private QualitativeAnalysis.SpicinessInsight spicinessWithSwearWords() {
        return new QualitativeAnalysis.SpicinessInsight(
                SpeakerRole.SELF,
                30,
                "비속어가 나타났어요.",
                List.of(new QualitativeAnalysis.SwearWordUsage(
                        "미친",
                        2,
                        List.of(1L)
                )),
                List.of(new QualitativeAnalysis.SpicinessObservation(
                        QualitativeAnalysis.SpicinessCategory.SWEAR_WORD,
                        "비속어 사용",
                        "강한 표현을 사용했어요.",
                        List.of(1L)
                ))
        );
    }

    private QualitativeAnalysis.ReactionStyleInsight reaction(
            SpeakerRole role,
            Long evidenceId,
            int thinkingPercent,
            int feelingPercent
    ) {
        return new QualitativeAnalysis.ReactionStyleInsight(
                role,
                thinkingPercent,
                feelingPercent,
                "정보와 감정에 함께 반응했어요.",
                List.of(new QualitativeAnalysis.ReactionExample(
                        QualitativeAnalysis.ReactionCategory.MIXED,
                        "혼합 반응",
                        "정보와 감정에 함께 반응했어요.",
                        List.of(evidenceId)
                ))
        );
    }

    private QualitativeAnalysis copy(
            QualitativeAnalysis valid,
            QualitativeAnalysis.Overview overview,
            List<QualitativeAnalysis.TimelineItem> timeline,
            List<QualitativeAnalysis.SpeakerInsight> speakerInsights,
            List<QualitativeAnalysis.ScenarioInsight> scenarioInsights
    ) {
        return new QualitativeAnalysis(
                overview,
                timeline,
                valid.topics(),
                valid.characterInsights(),
                speakerInsights,
                valid.interestInsights(),
                valid.spicinessInsights(),
                valid.reactionStyleInsights(),
                scenarioInsights
        );
    }
}
