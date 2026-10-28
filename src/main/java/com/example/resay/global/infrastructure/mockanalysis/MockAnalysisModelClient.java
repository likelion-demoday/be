package com.example.resay.global.infrastructure.mockanalysis;

import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.port.AnalysisModelClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Primary
@Profile("mock")
@Component
@ConditionalOnProperty(
        prefix = "analysis.mock",
        name = "model-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class MockAnalysisModelClient implements AnalysisModelClient {

    private final ObjectMapper objectMapper;

    public MockAnalysisModelClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public AnalysisModelResult analyze(AnalysisSource source) {
        List<AnalysisSegment> segments = source.segments();
        AnalysisSegment first = segments.get(0);
        AnalysisSegment second = segments.stream()
                .filter(segment -> segment.speakerRole() != first.speakerRole())
                .findFirst()
                .orElseThrow();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("overview", evidenceItem(
                "두 사람이 주제를 주고받은 대화",
                "두 사람이 주제를 주고받으며 대화를 이어가요.",
                List.of(first.segmentId(), second.segmentId())
        ));
        response.put("timeline", List.of(
                evidenceItem(
                        "대화 시작",
                        "한 사람이 주제를 꺼내자 상대가 자연스럽게 반응해요.",
                        List.of(first.segmentId(), second.segmentId())
                ),
                evidenceItem(
                        "대화 전개",
                        "서로의 생각을 덧붙이며 대화를 이어가요.",
                        List.of(segments.get(segments.size() - 2).segmentId(), segments.get(segments.size() - 1).segmentId())
                )
        ));
        response.put("topics", List.of(topic(segments)));
        response.put("characterInsights", roleItems(source, this::characterInsight));
        response.put("speakerInsights", speakerInsights(source));
        response.put("interestInsights", roleItems(source, this::interestInsight));
        response.put("spicinessInsights", roleItems(source, this::spicinessInsight));
        response.put("reactionStyleInsights", roleItems(source, this::reactionStyleInsight));
        response.put("scenarioInsights", List.of(scenarioInsight(source, first, second)));
        response.put("representativeEvidences", representativeEvidences(source));
        response.put("relationshipSummary", evidenceItem(
                "주고받으며 이어가는 두 사람",
                "이번 대화에서는 서로의 말에 반응하며 주제를 이어가는 흐름이 보여요.",
                List.of(first.segmentId(), second.segmentId())
        ));
        response.put("reportMetrics", reportMetrics(source));
        response.put("speakerTendencies", speakerTendencies(source));
        response.put("coachingSuggestions", coachingSuggestions(source));

        try {
            return new AnalysisModelResult(
                    objectMapper.writeValueAsString(response),
                    "mock-analysis-model",
                    "mock-prompt-v3",
                    "analysis-result-v7"
            );
        } catch (Exception exception) {
            throw new IllegalStateException("목업 정성 분석 결과를 만들 수 없습니다.", exception);
        }
    }

    private List<Map<String, Object>> reportMetrics(AnalysisSource source) {
        String type = switch (source.scenario()) {
            case FRIEND_DAILY -> "COMPLIMENT";
            case COUPLE_DAILY -> "DIRECT_AFFECTION";
            case COUPLE_CONFLICT -> "DIRECT_EXPRESSION";
            case PARENT_CHILD_CONFLICT -> "ADVICE";
        };
        return source.scenario().requiredRoles().stream()
                .sorted()
                .map(role -> {
                    AnalysisSegment evidence = evidenceFor(source, role);
                    Map<String, Object> metric = new LinkedHashMap<>();
                    metric.put("type", type);
                    metric.put("speakerRole", role);
                    metric.put("value", source.scenario() == com.example.resay.domain.analysis.model.AnalysisScenario.FRIEND_DAILY
                            ? 1 : 50);
                    metric.put("unit", source.scenario() == com.example.resay.domain.analysis.model.AnalysisScenario.FRIEND_DAILY
                            ? "COUNT" : "PERCENT");
                    metric.put("title", "이번 대화의 표현 지표");
                    metric.put("description", "이번 대화에서 확인된 표현을 기준으로 보여드려요.");
                    metric.put("evidenceSegmentIds", List.of(evidence.segmentId()));
                    return metric;
                })
                .toList();
    }

    private List<Map<String, Object>> speakerTendencies(AnalysisSource source) {
        return switch (source.scenario()) {
            case FRIEND_DAILY, COUPLE_DAILY -> List.of();
            case COUPLE_CONFLICT, PARENT_CHILD_CONFLICT -> source.scenario().requiredRoles().stream()
                    .sorted()
                    .map(role -> {
                        AnalysisSegment evidence = evidenceFor(source, role);
                        Map<String, Object> tendency = new LinkedHashMap<>();
                        tendency.put("speakerRole", role);
                        tendency.put("label", "설명하며 확인하는 편");
                        tendency.put("description", "이번 대화에서는 자신의 생각을 설명하고 상대의 반응을 확인해요.");
                        tendency.put("evidenceSegmentIds", List.of(evidence.segmentId()));
                        return tendency;
                    })
                    .toList();
        };
    }

    private List<Map<String, Object>> coachingSuggestions(AnalysisSource source) {
        if (source.scenario() == com.example.resay.domain.analysis.model.AnalysisScenario.FRIEND_DAILY
                || source.scenario() == com.example.resay.domain.analysis.model.AnalysisScenario.COUPLE_DAILY) {
            return List.of();
        }
        List<Long> evidenceIds = source.segments().stream()
                .limit(2)
                .map(AnalysisSegment::segmentId)
                .toList();
        return java.util.stream.IntStream.rangeClosed(1, 3)
                .mapToObj(order -> {
                    Map<String, Object> suggestion = new LinkedHashMap<>();
                    suggestion.put("order", order);
                    suggestion.put("title", order + "단계 대화 제안");
                    suggestion.put("description", "상대의 말을 확인한 뒤 내 생각을 짧게 전해 보세요.");
                    suggestion.put("suggestedPhrase", "네 말을 먼저 이해하고 싶어. 이 부분을 조금 더 말해줄래?");
                    suggestion.put("evidenceSegmentIds", evidenceIds);
                    return suggestion;
                })
                .toList();
    }

    private List<Map<String, Object>> representativeEvidences(AnalysisSource source) {
        return switch (source.scenario()) {
            case FRIEND_DAILY, COUPLE_DAILY -> source.scenario().requiredRoles().stream()
                    .sorted()
                    .map(role -> representativeEvidence(
                            "REACTION_STYLE_EXAMPLE",
                            evidenceFor(source, role),
                            "MIXED"
                    ))
                    .toList();
            case COUPLE_CONFLICT -> {
                AnalysisSegment self = evidenceFor(source, SpeakerRole.SELF);
                AnalysisSegment partner = evidenceFor(source, SpeakerRole.PARTNER);
                yield List.of(
                        representativeEvidence("EMOTION_EXPRESSION", self, null),
                        representativeEvidence("MISSED_QUESTION", self, null),
                        representativeEvidence("FOLLOWING_RESPONSE", partner, null),
                        representativeEvidence("SPEAKER_REACTION", self, null),
                        representativeEvidence("SPEAKER_REACTION", partner, null),
                        representativeEvidence("RECOVERY_START", partner, null)
                );
            }
            case PARENT_CHILD_CONFLICT -> {
                AnalysisSegment parent = evidenceFor(source, SpeakerRole.PARENT);
                AnalysisSegment child = evidenceFor(source, SpeakerRole.CHILD);
                yield List.of(
                        representativeEvidence("CHILD_POSITION", child, null),
                        representativeEvidence("FOLLOWING_ADVICE", parent, null),
                        representativeEvidence("UNASKED_QUESTION", child, null),
                        representativeEvidence("WORRY_START", parent, null),
                        representativeEvidence("EXPRESSION_TURNING_POINT", parent, null),
                        representativeEvidence("CLOSING_RESPONSE", child, null)
                );
            }
        };
    }

    private Map<String, Object> representativeEvidence(
            String type,
            AnalysisSegment segment,
            String reactionCategory
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", type);
        result.put("speakerRole", segment.speakerRole());
        result.put("reactionCategory", reactionCategory);
        result.put("segmentId", segment.segmentId());
        result.put("displayContent", segment.content());
        return result;
    }

    private List<Map<String, Object>> speakerInsights(AnalysisSource source) {
        return source.scenario().requiredRoles().stream()
                .sorted()
                .map(role -> {
                    AnalysisSegment evidence = source.segments().stream()
                            .filter(segment -> segment.speakerRole() == role)
                            .findFirst()
                            .orElseThrow();
                    Map<String, Object> pattern = new LinkedHashMap<>();
                    pattern.put("category", "EXPRESSION_PATTERN");
                    pattern.put("title", roleTitle(role));
                    pattern.put("description", "이번 대화에서 이런 표현 방식을 자주 보여요.");
                    pattern.put("evidenceSegmentIds", List.of(evidence.segmentId()));

                    Map<String, Object> insight = new LinkedHashMap<>();
                    insight.put("speakerRole", role);
                    insight.put("patterns", List.of(pattern));
                    insight.put("frequentExpressionSummary", null);
                    insight.put("frequentExpressions", List.of());
                    return insight;
                })
                .toList();
    }

    private List<Map<String, Object>> roleItems(
            AnalysisSource source,
            java.util.function.BiFunction<SpeakerRole, AnalysisSegment, Map<String, Object>> factory
    ) {
        return source.scenario().requiredRoles().stream()
                .sorted()
                .map(role -> factory.apply(role, evidenceFor(source, role)))
                .toList();
    }

    private AnalysisSegment evidenceFor(AnalysisSource source, SpeakerRole role) {
        return source.segments().stream()
                .filter(segment -> segment.speakerRole() == role)
                .findFirst()
                .orElseThrow();
    }

    private Map<String, Object> topic(List<AnalysisSegment> segments) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", "주요 대화 주제");
        result.put("description", "두 사람이 가장 오래 이야기한 주제예요.");
        result.put("segmentIds", segments.stream().map(AnalysisSegment::segmentId).toList());
        return result;
    }

    private Map<String, Object> characterInsight(SpeakerRole role, AnalysisSegment evidence) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("speakerRole", role);
        result.put("name", roleTitle(role));
        result.put("description", "이번 대화에서 보여준 표현을 담은 캐릭터예요.");
        result.put("evidenceSegmentIds", List.of(evidence.segmentId()));
        return result;
    }

    private Map<String, Object> interestInsight(SpeakerRole role, AnalysisSegment evidence) {
        Map<String, Object> observation = categorizedItem(
                "QUESTION",
                "대화 참여",
                "상대의 말에 반응하며 대화를 이어가요.",
                evidence.segmentId()
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("speakerRole", role);
        result.put("score", 50);
        result.put("description", "이번 대화에서 보여준 관심 표현을 기준으로 한 점수예요.");
        result.put("observations", List.of(observation));
        return result;
    }

    private Map<String, Object> spicinessInsight(SpeakerRole role, AnalysisSegment evidence) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("speakerRole", role);
        result.put("score", 0);
        result.put("description", "날선 말보다는 부드럽게 받아주는 편이에요.");
        result.put("swearWords", List.of());
        result.put("observations", List.of());
        return result;
    }

    private Map<String, Object> reactionStyleInsight(SpeakerRole role, AnalysisSegment evidence) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("speakerRole", role);
        result.put("thinkingPercent", 50);
        result.put("feelingPercent", 50);
        result.put("description", "상황을 살피는 반응과 공감하는 반응을 함께 보여요.");
        result.put("examples", List.of(categorizedItem(
                "MIXED",
                "혼합 반응",
                "내용과 감정에 함께 반응해요.",
                evidence.segmentId()
        )));
        return result;
    }

    private Map<String, Object> categorizedItem(
            String category,
            String title,
            String description,
            Long evidenceSegmentId
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", category);
        result.put("title", title);
        result.put("description", description);
        result.put("evidenceSegmentIds", List.of(evidenceSegmentId));
        return result;
    }

    private Map<String, Object> scenarioInsight(
            AnalysisSource source,
            AnalysisSegment first,
            AnalysisSegment second
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("category", switch (source.scenario()) {
            case FRIEND_DAILY, COUPLE_DAILY -> "COMMON_INTEREST";
            case COUPLE_CONFLICT, PARENT_CHILD_CONFLICT -> "CONFLICT_TOPIC";
        });
        result.put("speakerRoles", source.scenario().requiredRoles().stream().sorted().toList());
        result.put("title", "함께 이어간 핵심 주제");
        result.put("description", "같은 주제를 두고 서로의 생각을 나눠요.");
        result.put("evidenceSegmentIds", List.of(first.segmentId(), second.segmentId()));
        return result;
    }

    private Map<String, Object> evidenceItem(
            String title,
            String description,
            List<Long> evidenceSegmentIds
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", title);
        result.put("description", description);
        result.put("evidenceSegmentIds", evidenceSegmentIds);
        return result;
    }

    private String roleTitle(SpeakerRole role) {
        return switch (role) {
            case SELF -> "내 표현 방식";
            case PARTNER -> "상대방의 표현 방식";
            case FRIEND -> "친구의 표현 방식";
            case PARENT -> "부모 화자의 표현 방식";
            case CHILD -> "자녀 화자의 표현 방식";
        };
    }
}
