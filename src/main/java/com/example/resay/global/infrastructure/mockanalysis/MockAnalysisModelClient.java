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
                "목업 전사문을 바탕으로 대화의 주요 흐름을 요약했습니다.",
                List.of(first.segmentId(), second.segmentId())
        ));
        response.put("timeline", List.of(
                evidenceItem(
                        "대화 시작",
                        "첫 번째 화자가 주제를 꺼내고 상대 화자가 반응했습니다.",
                        List.of(first.segmentId(), second.segmentId())
                ),
                evidenceItem(
                        "대화 전개",
                        "두 화자가 각자의 생각을 덧붙이며 대화를 이어갔습니다.",
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

        try {
            return new AnalysisModelResult(
                    objectMapper.writeValueAsString(response),
                    "mock-analysis-model",
                    "mock-prompt-v2",
                    "analysis-result-v4"
            );
        } catch (Exception exception) {
            throw new IllegalStateException("목업 정성 분석 결과를 만들 수 없습니다.", exception);
        }
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
                    pattern.put("description", "이번 대화에서 반복적으로 관찰할 수 있는 표현 방식을 정리했습니다.");
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
        result.put("description", "두 화자가 가장 길게 이어간 주제를 정리했습니다.");
        result.put("segmentIds", segments.stream().map(AnalysisSegment::segmentId).toList());
        return result;
    }

    private Map<String, Object> characterInsight(SpeakerRole role, AnalysisSegment evidence) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("speakerRole", role);
        result.put("name", roleTitle(role));
        result.put("description", "이번 대화에서 관찰된 표현을 바탕으로 만든 목업 캐릭터입니다.");
        result.put("evidenceSegmentIds", List.of(evidence.segmentId()));
        return result;
    }

    private Map<String, Object> interestInsight(SpeakerRole role, AnalysisSegment evidence) {
        Map<String, Object> observation = categorizedItem(
                "QUESTION",
                "대화 참여",
                "상대의 말에 반응하며 대화를 이어갔습니다.",
                evidence.segmentId()
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("speakerRole", role);
        result.put("score", 50);
        result.put("description", "이번 대화에서 관찰된 관심 표현을 기준으로 만든 목업 점수입니다.");
        result.put("observations", List.of(observation));
        return result;
    }

    private Map<String, Object> spicinessInsight(SpeakerRole role, AnalysisSegment evidence) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("speakerRole", role);
        result.put("score", 0);
        result.put("description", "목업 발화에서는 강한 표현이 관찰되지 않았습니다.");
        result.put("observations", List.of());
        return result;
    }

    private Map<String, Object> reactionStyleInsight(SpeakerRole role, AnalysisSegment evidence) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("speakerRole", role);
        result.put("thinkingPercent", 50);
        result.put("feelingPercent", 50);
        result.put("description", "정보 중심 반응과 공감 중심 반응이 함께 나타난 목업 결과입니다.");
        result.put("examples", List.of(categorizedItem(
                "MIXED",
                "혼합 반응",
                "내용과 감정에 함께 반응했습니다.",
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
        result.put("description", "두 화자가 같은 주제에 각자의 반응을 보였습니다.");
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
