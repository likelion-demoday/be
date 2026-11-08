package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.port.AnalysisModelClient;
import com.example.resay.domain.analysis.service.AnalysisFailureCategory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.ObjectMapper;
// AnalysisModelClient를 실제 LINER 호출로 연결하는 어댑터
@Slf4j
@Component
public class LinerAnalysisModelClient implements AnalysisModelClient {

    private final LinerApiClient linerApiClient;
    private final LinerAnalysisRequestFactory requestFactory;
    private final LinerAnalysisResponseGrounder responseGrounder;
    private final LinerAnalysisResponseValidator responseValidator;
    private final ObjectMapper objectMapper;

    public LinerAnalysisModelClient(
            LinerApiClient linerApiClient,
            LinerAnalysisRequestFactory requestFactory,
            LinerAnalysisResponseGrounder responseGrounder,
            LinerAnalysisResponseValidator responseValidator,
            ObjectMapper objectMapper
    ) {
        this.linerApiClient = linerApiClient;
        this.requestFactory = requestFactory;
        this.responseGrounder = responseGrounder;
        this.responseValidator = responseValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    public AnalysisModelResult analyze(AnalysisSource source) {
        LinerChatResult result;
        try {
            result = linerApiClient.chat(requestFactory.create(source));
        } catch (LinerApiException exception) {
            throw apiFailure(exception);
        }
        validateCompletion(result);

        QualitativeAnalysis response = parseResponse(result.content());
        QualitativeAnalysis groundedResponse = responseGrounder.ground(source, response);
        QualitativeAnalysis responseWithRequiredMetrics =
                ensureRequiredPercentReportMetrics(source, groundedResponse);
        QualitativeAnalysis responseWithRequiredSpiciness =
                ensureSpicinessInsights(source, responseWithRequiredMetrics);
        QualitativeAnalysis responseWithRequiredCoaching =
                ensureParentChildCoachingSuggestions(source, responseWithRequiredSpiciness);
        QualitativeAnalysis responseWithRequiredFields =
                ensureParentChildRepresentativeEvidences(
                        source,
                        responseWithRequiredCoaching
                );
        QualitativeAnalysis validatedResponse = validateWithBestEffortFallback(
                source,
                responseWithRequiredFields
        );

        return new AnalysisModelResult(
                serializeResponse(validatedResponse),
                result.response().model(),
                requestFactory.promptVersion(),
                requestFactory.schemaVersion()
        );
    }

    private QualitativeAnalysis ensureRequiredPercentReportMetrics(
            AnalysisSource source,
            QualitativeAnalysis response
    ) {
        List<QualitativeAnalysis.ReportMetricType> requiredTypes =
                LinerAnalysisPolicy.requiredPercentReportMetricTypes(source.scenario());
        Map<ReportMetricKey, QualitativeAnalysis.ReportMetric> metricsByKey = new HashMap<>();
        List<QualitativeAnalysis.ReportMetric> completedMetrics = new ArrayList<>();

        for (QualitativeAnalysis.ReportMetric metric : response.reportMetrics()) {
            boolean requiredPercentMetric = metric != null
                    && metric.unit() == QualitativeAnalysis.ReportMetricUnit.PERCENT
                    && requiredTypes.contains(metric.type());
            if (!requiredPercentMetric) {
                completedMetrics.add(metric);
                continue;
            }
            if (source.scenario().requiredRoles().contains(metric.speakerRole())) {
                metricsByKey.putIfAbsent(
                        new ReportMetricKey(metric.type(), metric.speakerRole()),
                        metric
                );
            }
        }

        int missingCount = 0;
        for (var speaker : source.speakers()) {
            for (QualitativeAnalysis.ReportMetricType type : requiredTypes) {
                QualitativeAnalysis.ReportMetric metric = metricsByKey.get(
                        new ReportMetricKey(type, speaker.speakerRole())
                );
                if (metric == null) {
                    missingCount++;
                    completedMetrics.add(defaultPercentMetric(type, speaker.speakerRole()));
                } else {
                    completedMetrics.add(normalizePercentMetric(metric));
                }
            }
        }

        if (missingCount > 0) {
            log.warn(
                    "화자별 필수 보고서 비율 지표를 안전 기본값으로 보완: "
                            + "recordingId={}, scenario={}, count={}",
                    source.recordingId(),
                    source.scenario(),
                    missingCount
            );
        }
        return copyWithReportMetrics(response, completedMetrics);
    }

    private QualitativeAnalysis.ReportMetric normalizePercentMetric(
            QualitativeAnalysis.ReportMetric metric
    ) {
        int value = Math.max(0, Math.min(100, metric.value()));
        return new QualitativeAnalysis.ReportMetric(
                metric.type(),
                metric.speakerRole(),
                value,
                metric.unit(),
                reportMetricTitle(metric.type()),
                StringUtils.hasText(metric.description())
                        ? metric.description()
                        : defaultReportMetricDescription(metric.type()),
                value == 0 ? List.of() : metric.evidenceSegmentIds(),
                value == 0 ? List.of() : metric.displayEvidences()
        );
    }

    private QualitativeAnalysis.ReportMetric defaultPercentMetric(
            QualitativeAnalysis.ReportMetricType type,
            SpeakerRole speakerRole
    ) {
        return new QualitativeAnalysis.ReportMetric(
                type,
                speakerRole,
                0,
                QualitativeAnalysis.ReportMetricUnit.PERCENT,
                reportMetricTitle(type),
                defaultReportMetricDescription(type),
                List.of(),
                List.of()
        );
    }

    private String reportMetricTitle(QualitativeAnalysis.ReportMetricType type) {
        return switch (type) {
            case SWEAR_WORD -> "비속어";
            case DIRECTNESS -> "직설력";
            case EXPRESSION_INTENSITY -> "표현 강도";
            case SOFTENING_EXPRESSION -> "완충 표현";
            case DIRECT_AFFECTION -> "직접 표현";
            case CARING_EXPRESSION -> "챙기는 말";
            case COMPLIMENT_OR_ENCOURAGEMENT -> "칭찬·응원";
            case AFFECTIONATE_ADDRESS -> "다정한 호칭";
            case DIRECT_EXPRESSION -> "직설 표현";
            case ASSERTIVE_EXPRESSION -> "단정 표현";
            case RECOVERY_EXPRESSION -> "회복 표현";
            case ADVICE -> "조언 표현";
            case EMOTION_CHECK -> "감정 확인";
            default -> throw new IllegalArgumentException("필수 비율 지표가 아닙니다: " + type);
        };
    }

    private String defaultReportMetricDescription(
            QualitativeAnalysis.ReportMetricType type
    ) {
        return switch (type) {
            case SWEAR_WORD -> "이번 대화에서 비속어가 관찰되지 않았어요.";
            case DIRECTNESS -> "이번 대화에서 직설적인 표현이 관찰되지 않았어요.";
            case EXPRESSION_INTENSITY -> "이번 대화에서 강한 표현이 관찰되지 않았어요.";
            case SOFTENING_EXPRESSION -> "이번 대화에서 완충 표현이 관찰되지 않았어요.";
            case DIRECT_AFFECTION -> "이번 대화에서 직접적인 애정 표현이 관찰되지 않았어요.";
            case CARING_EXPRESSION -> "이번 대화에서 상대를 챙기는 말이 관찰되지 않았어요.";
            case COMPLIMENT_OR_ENCOURAGEMENT -> "이번 대화에서 칭찬이나 응원이 관찰되지 않았어요.";
            case AFFECTIONATE_ADDRESS -> "이번 대화에서 다정한 호칭이 관찰되지 않았어요.";
            case DIRECT_EXPRESSION -> "이번 대화에서 직설적인 표현이 관찰되지 않았어요.";
            case ASSERTIVE_EXPRESSION -> "이번 대화에서 단정적인 표현이 관찰되지 않았어요.";
            case RECOVERY_EXPRESSION -> "이번 대화에서 관계 회복 표현이 관찰되지 않았어요.";
            case ADVICE -> "이번 대화에서 조언 표현이 관찰되지 않았어요.";
            case EMOTION_CHECK -> "이번 대화에서 감정을 확인하는 표현이 관찰되지 않았어요.";
            default -> throw new IllegalArgumentException("필수 비율 지표가 아닙니다: " + type);
        };
    }

    private QualitativeAnalysis copyWithReportMetrics(
            QualitativeAnalysis response,
            List<QualitativeAnalysis.ReportMetric> reportMetrics
    ) {
        return new QualitativeAnalysis(
                response.overview(),
                response.timeline(),
                response.topics(),
                response.characterInsights(),
                response.speakerInsights(),
                response.interestInsights(),
                response.spicinessInsights(),
                response.reactionStyleInsights(),
                response.scenarioInsights(),
                response.representativeEvidences(),
                response.relationshipSummary(),
                reportMetrics,
                response.speakerTendencies(),
                response.coachingSuggestions()
        );
    }

    private record ReportMetricKey(
            QualitativeAnalysis.ReportMetricType type,
            SpeakerRole speakerRole
    ) {
    }

    private QualitativeAnalysis ensureSpicinessInsights(
            AnalysisSource source,
            QualitativeAnalysis response
    ) {
        List<QualitativeAnalysis.SpicinessInsight> originalInsights =
                response.spicinessInsights() == null
                        ? List.of()
                        : response.spicinessInsights();
        Map<SpeakerRole, QualitativeAnalysis.SpicinessInsight> insightsByRole = new HashMap<>();
        originalInsights.stream()
                .filter(insight -> insight != null
                        && source.scenario().requiredRoles().contains(insight.speakerRole()))
                .forEach(insight -> insightsByRole.putIfAbsent(insight.speakerRole(), insight));

        boolean complete = originalInsights.size()
                == source.scenario().requiredRoles().size()
                && insightsByRole.keySet().equals(source.scenario().requiredRoles());
        if (complete) {
            return response;
        }

        List<QualitativeAnalysis.SpicinessInsight> completedInsights = source.speakers().stream()
                .map(speaker -> insightsByRole.getOrDefault(
                        speaker.speakerRole(),
                        new QualitativeAnalysis.SpicinessInsight(
                                speaker.speakerRole(),
                                0,
                                "강한 표현으로 분류된 발화가 없어 0점으로 표시해요.",
                                List.of(),
                                List.of()
                        )
                ))
                .toList();
        log.warn(
                "화자별 표독력 항목을 안전 기본값으로 보완: recordingId={}, scenario={}",
                source.recordingId(),
                source.scenario()
        );
        return new QualitativeAnalysis(
                response.overview(),
                response.timeline(),
                response.topics(),
                response.characterInsights(),
                response.speakerInsights(),
                response.interestInsights(),
                completedInsights,
                response.reactionStyleInsights(),
                response.scenarioInsights(),
                response.representativeEvidences(),
                response.relationshipSummary(),
                response.reportMetrics(),
                response.speakerTendencies(),
                response.coachingSuggestions()
        );
    }

    private QualitativeAnalysis validateWithBestEffortFallback(
            AnalysisSource source,
            QualitativeAnalysis response
    ) {
        try {
            responseValidator.validate(source, response);
            return response;
        } catch (LinerAnalysisException validationException) {
            if (validationException.getCategory()
                    != AnalysisFailureCategory.RESPONSE_VALIDATION_ERROR) {
                throw validationException;
            }

            // JSON Schema 파싱과 Grounder 보정까지 끝난 응답은 가능한 범위에서 저장한다.
            // 한 부가 항목의 오류 때문에 정상 보고서 항목을 함께 삭제하지 않는다.
            // 화면 조립에 꼭 필요한 타임라인·주제 근거가 깨졌을 때만 검증 실패를 유지한다.
            if (!isRenderableCore(source, response)) {
                throw validationException;
            }
            log.warn(
                    "분석 응답 의미 검증 실패, 전체 보고서 항목을 보존해 저장: "
                            + "recordingId={}, scenario={}, promptVersion={}, "
                            + "schemaVersion={}, reason={}",
                    source.recordingId(),
                    source.scenario(),
                    requestFactory.promptVersion(),
                    requestFactory.schemaVersion(),
                    validationException.getMessage()
            );
            return response;
        }
    }

    private boolean isRenderableCore(AnalysisSource source, QualitativeAnalysis response) {
        if (response.overview() == null
                || response.timeline() == null || response.timeline().isEmpty()
                || response.topics() == null || response.topics().isEmpty()
                || response.characterInsights() == null
                || response.speakerInsights() == null
                || response.interestInsights() == null
                || response.spicinessInsights() == null
                || response.reactionStyleInsights() == null
                || response.scenarioInsights() == null) {
            return false;
        }

        Set<Long> segmentIds = source.segments().stream()
                .map(AnalysisSegment::segmentId)
                .collect(Collectors.toSet());
        boolean timelineRenderable = response.timeline().stream().allMatch(item ->
                item != null
                        && item.evidenceSegmentIds() != null
                        && !item.evidenceSegmentIds().isEmpty()
                        && segmentIds.containsAll(item.evidenceSegmentIds()));
        boolean topicsRenderable = response.topics().stream().allMatch(topic ->
                topic != null
                        && topic.segmentIds() != null
                        && !topic.segmentIds().isEmpty()
                        && segmentIds.containsAll(topic.segmentIds()));
        return timelineRenderable && topicsRenderable;
    }

    private QualitativeAnalysis ensureParentChildCoachingSuggestions(
            AnalysisSource source,
            QualitativeAnalysis response
    ) {
        if (source.scenario() != AnalysisScenario.PARENT_CHILD_CONFLICT
                || hasValidCoachingSuggestions(source, response.coachingSuggestions())) {
            return response;
        }

        List<QualitativeAnalysis.CoachingSuggestion> fallbackSuggestions =
                parentChildCoachingFallback(source, response);
        log.warn(
                "부모·자녀 다시 대화하기 항목을 안전 대안으로 보완: recordingId={}, scenario={}",
                source.recordingId(),
                source.scenario()
        );
        return new QualitativeAnalysis(
                response.overview(),
                response.timeline(),
                response.topics(),
                response.characterInsights(),
                response.speakerInsights(),
                response.interestInsights(),
                response.spicinessInsights(),
                response.reactionStyleInsights(),
                response.scenarioInsights(),
                response.representativeEvidences(),
                response.relationshipSummary(),
                response.reportMetrics(),
                response.speakerTendencies(),
                fallbackSuggestions
        );
    }

    private QualitativeAnalysis ensureParentChildRepresentativeEvidences(
            AnalysisSource source,
            QualitativeAnalysis response
    ) {
        if (source.scenario() != AnalysisScenario.PARENT_CHILD_CONFLICT
                || hasValidParentChildRepresentativeEvidences(source, response)) {
            return response;
        }

        List<QualitativeAnalysis.RepresentativeEvidence> recovered = List.of(
                parentChildRepresentativeEvidence(
                        source,
                        response,
                        QualitativeAnalysis.ReportEvidenceType.CHILD_POSITION,
                        SpeakerRole.CHILD
                ),
                parentChildRepresentativeEvidence(
                        source,
                        response,
                        QualitativeAnalysis.ReportEvidenceType.FOLLOWING_ADVICE,
                        SpeakerRole.PARENT
                ),
                parentChildRepresentativeEvidence(
                        source,
                        response,
                        QualitativeAnalysis.ReportEvidenceType.UNASKED_QUESTION,
                        SpeakerRole.CHILD
                ),
                parentChildRepresentativeEvidence(
                        source,
                        response,
                        QualitativeAnalysis.ReportEvidenceType.WORRY_START,
                        SpeakerRole.PARENT
                ),
                parentChildRepresentativeEvidence(
                        source,
                        response,
                        QualitativeAnalysis.ReportEvidenceType.EXPRESSION_TURNING_POINT,
                        SpeakerRole.PARENT
                ),
                parentChildRepresentativeEvidence(
                        source,
                        response,
                        QualitativeAnalysis.ReportEvidenceType.CLOSING_RESPONSE,
                        SpeakerRole.CHILD
                )
        );
        log.warn(
                "부모·자녀 대표 근거를 유형별 안전 대안으로 보완: recordingId={}, scenario={}",
                source.recordingId(),
                source.scenario()
        );
        return new QualitativeAnalysis(
                response.overview(),
                response.timeline(),
                response.topics(),
                response.characterInsights(),
                response.speakerInsights(),
                response.interestInsights(),
                response.spicinessInsights(),
                response.reactionStyleInsights(),
                response.scenarioInsights(),
                recovered,
                response.relationshipSummary(),
                response.reportMetrics(),
                response.speakerTendencies(),
                response.coachingSuggestions()
        );
    }

    private boolean hasValidParentChildRepresentativeEvidences(
            AnalysisSource source,
            QualitativeAnalysis response
    ) {
        if (response.representativeEvidences() == null
                || response.representativeEvidences().size() != 6) {
            return false;
        }
        try {
            responseValidator.validateRepresentativeEvidencesOnly(source, response);
            return true;
        } catch (LinerAnalysisException exception) {
            if (exception.getCategory() != AnalysisFailureCategory.RESPONSE_VALIDATION_ERROR) {
                throw exception;
            }
            return false;
        }
    }

    private QualitativeAnalysis.RepresentativeEvidence parentChildRepresentativeEvidence(
            AnalysisSource source,
            QualitativeAnalysis response,
            QualitativeAnalysis.ReportEvidenceType type,
            SpeakerRole role
    ) {
        Set<Long> validRoleSegmentIds = source.segments().stream()
                .filter(segment -> segment.speakerRole() == role)
                .map(AnalysisSegment::segmentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<QualitativeAnalysis.RepresentativeEvidence> existingEvidences =
                response.representativeEvidences() == null
                        ? List.of()
                        : response.representativeEvidences();
        QualitativeAnalysis.RepresentativeEvidence existing =
                existingEvidences.stream()
                        .filter(item -> item != null && item.type() == type)
                        .filter(item -> item.speakerRole() == role)
                        .filter(item -> validRoleSegmentIds.contains(item.segmentId()))
                        .filter(item -> StringUtils.hasText(item.displayContent()))
                        .findFirst()
                        .orElse(null);
        if (existing != null) {
            return new QualitativeAnalysis.RepresentativeEvidence(
                    type,
                    role,
                    null,
                    existing.segmentId(),
                    existing.displayContent()
            );
        }

        Long segmentId = representativeCandidateIds(response, type).stream()
                .filter(validRoleSegmentIds::contains)
                .reduce((first, second) -> type
                        == QualitativeAnalysis.ReportEvidenceType.CLOSING_RESPONSE
                        ? second
                        : first)
                .orElseGet(() -> topicEvidenceIds(response).stream()
                        .filter(validRoleSegmentIds::contains)
                        .reduce((first, second) -> type
                                == QualitativeAnalysis.ReportEvidenceType.CLOSING_RESPONSE
                                ? second
                                : first)
                        .orElseGet(() -> type
                                == QualitativeAnalysis.ReportEvidenceType.CLOSING_RESPONSE
                                ? source.segments().stream()
                                        .filter(segment -> segment.speakerRole() == role)
                                        .reduce((first, second) -> second)
                                        .orElseThrow()
                                        .segmentId()
                                : validRoleSegmentIds.iterator().next()));
        AnalysisSegment segment = source.segments().stream()
                .filter(item -> item.segmentId().equals(segmentId))
                .findFirst()
                .orElseThrow();
        return new QualitativeAnalysis.RepresentativeEvidence(
                type,
                role,
                null,
                segmentId,
                segment.content()
        );
    }

    private List<Long> representativeCandidateIds(
            QualitativeAnalysis response,
            QualitativeAnalysis.ReportEvidenceType type
    ) {
        if (type == QualitativeAnalysis.ReportEvidenceType.FOLLOWING_ADVICE) {
            return response.reportMetrics().stream()
                    .filter(metric -> metric != null
                            && metric.type()
                            == QualitativeAnalysis.ReportMetricType.CONCRETE_ADVICE)
                    .flatMap(metric -> metric.evidenceSegmentIds().stream())
                    .distinct()
                    .toList();
        }
        QualitativeAnalysis.ScenarioInsightCategory category = switch (type) {
            case CHILD_POSITION -> QualitativeAnalysis.ScenarioInsightCategory.CONFLICT_POSITION;
            case UNASKED_QUESTION ->
                    QualitativeAnalysis.ScenarioInsightCategory.QUESTION_RESPONSE_STYLE;
            case WORRY_START -> QualitativeAnalysis.ScenarioInsightCategory.CARE_EXPRESSION;
            case EXPRESSION_TURNING_POINT ->
                    QualitativeAnalysis.ScenarioInsightCategory.TURNING_POINT;
            case CLOSING_RESPONSE -> QualitativeAnalysis.ScenarioInsightCategory.SOLUTION;
            default -> null;
        };
        if (category == null) {
            return List.of();
        }
        return response.scenarioInsights().stream()
                .filter(insight -> insight != null && insight.category() == category)
                .flatMap(insight -> insight.evidenceSegmentIds().stream())
                .distinct()
                .toList();
    }

    private List<Long> topicEvidenceIds(QualitativeAnalysis response) {
        return response.topics().stream()
                .flatMap(topic -> topic.segmentIds().stream())
                .distinct()
                .toList();
    }

    private boolean hasValidCoachingSuggestions(
            AnalysisSource source,
            List<QualitativeAnalysis.CoachingSuggestion> suggestions
    ) {
        if (suggestions == null
                || suggestions.size() != source.scenario().requiredRoles().size() * 3) {
            return false;
        }
        Set<Long> segmentIds = source.segments().stream()
                .map(AnalysisSegment::segmentId)
                .collect(Collectors.toSet());
        Map<SpeakerRole, Set<Integer>> ordersByRole = new HashMap<>();
        for (QualitativeAnalysis.CoachingSuggestion suggestion : suggestions) {
            if (suggestion == null
                    || !source.scenario().requiredRoles().contains(suggestion.speakerRole())
                    || suggestion.order() < 1 || suggestion.order() > 3
                    || !StringUtils.hasText(suggestion.title())
                    || !StringUtils.hasText(suggestion.description())
                    || !StringUtils.hasText(suggestion.suggestedPhrase())
                    || suggestion.evidenceSegmentIds() == null
                    || suggestion.evidenceSegmentIds().isEmpty()
                    || !segmentIds.containsAll(suggestion.evidenceSegmentIds())) {
                return false;
            }
            if (!ordersByRole.computeIfAbsent(
                    suggestion.speakerRole(),
                    ignored -> new HashSet<>()
            ).add(suggestion.order())) {
                return false;
            }
        }
        return source.scenario().requiredRoles().stream()
                .allMatch(role -> ordersByRole.getOrDefault(role, Set.of()).size() == 3);
    }

    private List<QualitativeAnalysis.CoachingSuggestion> parentChildCoachingFallback(
            AnalysisSource source,
            QualitativeAnalysis response
    ) {
        String topicTitle = conflictTopicTitle(response);
        List<Long> childEvidence = evidenceFor(
                source,
                response,
                QualitativeAnalysis.ScenarioInsightCategory.CONFLICT_POSITION,
                SpeakerRole.CHILD
        );
        List<Long> parentEvidence = evidenceFor(
                source,
                response,
                QualitativeAnalysis.ScenarioInsightCategory.CONFLICT_POSITION,
                SpeakerRole.PARENT
        );
        List<Long> solutionEvidence = evidenceFor(
                source,
                response,
                QualitativeAnalysis.ScenarioInsightCategory.SOLUTION,
                null
        );

        return List.of(
                new QualitativeAnalysis.CoachingSuggestion(
                        SpeakerRole.PARENT,
                        1,
                        "입장 확인: " + topicTitle,
                        "‘" + topicTitle + "’을 두고 자녀가 중요하게 생각하는 점을 먼저 확인해요.",
                        "‘" + topicTitle + "’에 대해 네가 가장 중요하게 생각하는 게 무엇인지 먼저 듣고 싶어.",
                        childEvidence
                ),
                new QualitativeAnalysis.CoachingSuggestion(
                        SpeakerRole.PARENT,
                        2,
                        "걱정 설명하기",
                        "‘" + topicTitle + "’에서 생긴 걱정을 자녀를 탓하지 않고 설명해요.",
                        "너를 탓하려는 건 아니고, 나는 이 부분이 걱정됐어.",
                        parentEvidence
                ),
                new QualitativeAnalysis.CoachingSuggestion(
                        SpeakerRole.PARENT,
                        3,
                        "다음 약속 정하기",
                        "비슷한 상황이 생겼을 때 서로 지킬 수 있는 구체적인 행동을 정해요.",
                        "다음에는 언제 어떻게 알려 줄지 우리 둘이 지킬 수 있는 방법으로 정해 볼까?",
                        solutionEvidence
                ),
                new QualitativeAnalysis.CoachingSuggestion(
                        SpeakerRole.CHILD,
                        1,
                        "걱정 확인: " + topicTitle,
                        "‘" + topicTitle + "’을 두고 부모가 걱정한 점을 먼저 확인해요.",
                        "내가 이렇게 이해했는데, 어떤 점이 가장 걱정됐는지 먼저 듣고 싶어.",
                        parentEvidence
                ),
                new QualitativeAnalysis.CoachingSuggestion(
                        SpeakerRole.CHILD,
                        2,
                        "필요한 방식 설명하기",
                        "‘" + topicTitle + "’에서 자신에게 필요했던 방식을 부모를 탓하지 않고 설명해요.",
                        "나는 그때 내 상황을 먼저 들어 주는 방식이 필요했어.",
                        childEvidence
                ),
                new QualitativeAnalysis.CoachingSuggestion(
                        SpeakerRole.CHILD,
                        3,
                        "다음 약속 제안하기",
                        "비슷한 상황이 생겼을 때 함께 지킬 수 있는 구체적인 행동을 제안해요.",
                        "다음에는 내가 먼저 알려 줄게. 그때는 답을 조금 기다려 줄 수 있어?",
                        solutionEvidence
                )
        );
    }

    private String conflictTopicTitle(QualitativeAnalysis response) {
        return response.scenarioInsights().stream()
                .filter(insight -> insight != null
                        && insight.category()
                        == QualitativeAnalysis.ScenarioInsightCategory.CONFLICT_TOPIC)
                .map(QualitativeAnalysis.ScenarioInsight::title)
                .filter(StringUtils::hasText)
                .findFirst()
                .or(() -> response.topics().stream()
                        .filter(topic -> topic != null && StringUtils.hasText(topic.title()))
                        .map(QualitativeAnalysis.Topic::title)
                        .findFirst())
                .orElse("이번 갈등");
    }

    private List<Long> evidenceFor(
            AnalysisSource source,
            QualitativeAnalysis response,
            QualitativeAnalysis.ScenarioInsightCategory category,
            SpeakerRole role
    ) {
        Set<Long> validSegmentIds = source.segments().stream()
                .map(AnalysisSegment::segmentId)
                .collect(Collectors.toSet());
        List<Long> evidenceIds = response.scenarioInsights().stream()
                .filter(insight -> insight != null && insight.category() == category)
                .filter(insight -> role == null
                        || insight.speakerRoles().contains(role))
                .flatMap(insight -> insight.evidenceSegmentIds().stream())
                .filter(validSegmentIds::contains)
                .distinct()
                .toList();
        if (!evidenceIds.isEmpty()) {
            return evidenceIds;
        }
        if (category == QualitativeAnalysis.ScenarioInsightCategory.SOLUTION) {
            int fromIndex = Math.max(0, source.segments().size() - 2);
            return source.segments().subList(fromIndex, source.segments().size()).stream()
                    .map(AnalysisSegment::segmentId)
                    .toList();
        }

        List<Long> roleEvidence = source.segments().stream()
                .filter(segment -> role == null || segment.speakerRole() == role)
                .map(AnalysisSegment::segmentId)
                .limit(2)
                .toList();
        if (!roleEvidence.isEmpty()) {
            return roleEvidence;
        }
        return List.of(source.segments().get(0).segmentId());
    }

    private void validateCompletion(LinerChatResult result) {
        String finishReason = result.response().choices().get(0).finishReason();
        if (!"stop".equals(finishReason)) {
            LinerChatResponse.Usage usage = result.response().usage();
            long completionTokens = usage != null ? usage.completionTokens() : 0;
            AnalysisFailureCategory category = "length".equals(finishReason)
                    ? AnalysisFailureCategory.RESPONSE_LENGTH_EXCEEDED
                    : AnalysisFailureCategory.RESPONSE_INCOMPLETE;
            throw new LinerAnalysisException(
                    category,
                    "incomplete_analysis_response",
                    "LINER 분석 응답이 정상적으로 완료되지 않았습니다. "
                            + "finishReason=" + finishReason
                            + ", completionTokens=" + completionTokens,
                    null
            );
        }
        if (!StringUtils.hasText(result.response().model())) {
            throw responseFormatFailure("LINER 분석 응답에 모델 정보가 없습니다.", null);
        }
    }

    private QualitativeAnalysis parseResponse(String content) {
        try {
            return objectMapper.readValue(content, QualitativeAnalysis.class);
        } catch (Exception exception) {
            throw responseFormatFailure("LINER 분석 응답을 해석할 수 없습니다.", exception);
        }
    }

    private String serializeResponse(QualitativeAnalysis response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception exception) {
            throw new LinerAnalysisException(
                    "analysis_response_serialization_failed",
                    "LINER 분석 결과를 저장 형식으로 변환할 수 없습니다.",
                    exception
            );
        }
    }

    private LinerAnalysisException responseFormatFailure(String message, Throwable cause) {
        return new LinerAnalysisException(
                AnalysisFailureCategory.RESPONSE_FORMAT_ERROR,
                "invalid_analysis_response",
                message,
                cause
        );
    }

    private LinerAnalysisException apiFailure(LinerApiException exception) {
        AnalysisFailureCategory category;
        Integer statusCode = exception.getStatusCode();
        if ("not_configured".equals(exception.getErrorCode())) {
            category = AnalysisFailureCategory.PROVIDER_CONFIGURATION;
        } else if (statusCode != null && (statusCode == 401 || statusCode == 403)) {
            category = AnalysisFailureCategory.PROVIDER_AUTHENTICATION;
        } else if (statusCode != null && statusCode == 429) {
            category = AnalysisFailureCategory.PROVIDER_RATE_LIMIT;
        } else if ("timeout".equals(exception.getErrorCode()) || (statusCode != null && statusCode == 408)) {
            category = AnalysisFailureCategory.PROVIDER_TIMEOUT;
        } else if ("transport_error".equals(exception.getErrorCode())) {
            category = AnalysisFailureCategory.PROVIDER_NETWORK;
        } else if (statusCode != null && statusCode >= 500) {
            category = AnalysisFailureCategory.PROVIDER_SERVER_ERROR;
        } else if ("invalid_response".equals(exception.getErrorCode())) {
            category = AnalysisFailureCategory.RESPONSE_FORMAT_ERROR;
        } else {
            category = AnalysisFailureCategory.PROVIDER_CONFIGURATION;
        }
        return new LinerAnalysisException(
                category,
                exception.getErrorCode(),
                "외부 분석 서비스 처리에 실패했습니다.",
                exception
        );
    }
}
