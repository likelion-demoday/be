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
@Profile({"local", "mock"})
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
        response.put("speakerInsights", speakerInsights(source));
        response.put("scenarioInsights", List.of(scenarioInsight(source, first, second)));

        try {
            return new AnalysisModelResult(
                    objectMapper.writeValueAsString(response),
                    "mock-analysis-model",
                    "mock-prompt-v1",
                    "analysis-result-v1"
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
                    return insight;
                })
                .toList();
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
