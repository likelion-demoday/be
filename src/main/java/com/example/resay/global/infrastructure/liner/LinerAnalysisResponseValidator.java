package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LinerAnalysisResponseValidator {

    public void validate(AnalysisSource source, QualitativeAnalysis response) {
        if (source == null || response == null) {
            throw invalidResponse("분석 입력 또는 응답이 비어 있습니다.");
        }

        Map<Long, AnalysisSegment> segmentsById = segmentsById(source.segments());
        validateOverview(response.overview(), segmentsById);
        validateTimeline(response.timeline(), segmentsById);
        validateSpeakerInsights(source, response.speakerInsights(), segmentsById);
        validateScenarioInsights(source, response.scenarioInsights(), segmentsById);
    }

    private void validateOverview(
            QualitativeAnalysis.Overview overview,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        if (overview == null) {
            throw invalidResponse("overview가 비어 있습니다.");
        }
        requireText(overview.title(), "overview.title");
        requireText(overview.description(), "overview.description");
        validateEvidence(overview.evidenceSegmentIds(), segmentsById, "overview");
    }

    private void validateTimeline(
            List<QualitativeAnalysis.TimelineItem> timeline,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        if (timeline == null || timeline.isEmpty() || timeline.size() > 6) {
            throw invalidResponse("timeline은 1개 이상 6개 이하여야 합니다.");
        }

        long previousStartMs = -1;
        for (QualitativeAnalysis.TimelineItem item : timeline) {
            if (item == null) {
                throw invalidResponse("timeline 항목이 비어 있습니다.");
            }
            requireText(item.title(), "timeline.title");
            requireText(item.description(), "timeline.description");
            validateEvidence(item.evidenceSegmentIds(), segmentsById, "timeline");

            long startMs = item.evidenceSegmentIds().stream()
                    .map(segmentsById::get)
                    .mapToLong(AnalysisSegment::startMs)
                    .min()
                    .orElseThrow();
            if (startMs < previousStartMs) {
                throw invalidResponse("timeline은 시간순이어야 합니다.");
            }
            previousStartMs = startMs;
        }
    }

    private void validateSpeakerInsights(
            AnalysisSource source,
            List<QualitativeAnalysis.SpeakerInsight> speakerInsights,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        if (speakerInsights == null) {
            throw invalidResponse("speakerInsights가 비어 있습니다.");
        }

        Set<SpeakerRole> roles = new HashSet<>();
        for (QualitativeAnalysis.SpeakerInsight insight : speakerInsights) {
            if (insight == null || insight.speakerRole() == null || insight.patterns() == null) {
                throw invalidResponse("speakerInsights 항목이 올바르지 않습니다.");
            }
            if (!roles.add(insight.speakerRole())) {
                throw invalidResponse("speakerInsights의 화자 역할은 중복될 수 없습니다.");
            }

            for (QualitativeAnalysis.SpeakerPattern pattern : insight.patterns()) {
                validateSpeakerPattern(
                        pattern,
                        insight.speakerRole(),
                        source.scenario(),
                        segmentsById
                );
            }
        }

        if (!roles.equals(source.scenario().requiredRoles())) {
            throw invalidResponse("speakerInsights의 화자 역할이 분석 시나리오와 일치하지 않습니다.");
        }
    }

    private void validateSpeakerPattern(
            QualitativeAnalysis.SpeakerPattern pattern,
            SpeakerRole speakerRole,
            AnalysisScenario scenario,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        if (pattern == null || pattern.category() == null) {
            throw invalidResponse("speaker pattern이 올바르지 않습니다.");
        }
        if (!LinerAnalysisPolicy.speakerPatternCategories(scenario).contains(pattern.category())) {
            throw invalidResponse("분석 시나리오에 맞지 않는 화자 패턴입니다.");
        }
        requireText(pattern.title(), "speakerPattern.title");
        requireText(pattern.description(), "speakerPattern.description");
        validateEvidence(pattern.evidenceSegmentIds(), segmentsById, "speakerPattern");

        boolean containsSpeakerEvidence = pattern.evidenceSegmentIds().stream()
                .map(segmentsById::get)
                .anyMatch(segment -> segment.speakerRole() == speakerRole);
        if (!containsSpeakerEvidence) {
            throw invalidResponse("화자별 관찰에는 해당 화자의 근거 발화가 필요합니다.");
        }
    }

    private void validateScenarioInsights(
            AnalysisSource source,
            List<QualitativeAnalysis.ScenarioInsight> scenarioInsights,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        if (scenarioInsights == null) {
            throw invalidResponse("scenarioInsights가 비어 있습니다.");
        }

        for (QualitativeAnalysis.ScenarioInsight insight : scenarioInsights) {
            if (insight == null || insight.category() == null || insight.speakerRoles() == null) {
                throw invalidResponse("scenario insight가 올바르지 않습니다.");
            }
            if (new HashSet<>(insight.speakerRoles()).size() != insight.speakerRoles().size()) {
                throw invalidResponse("scenario insight의 화자 역할은 중복될 수 없습니다.");
            }
            if (!source.scenario().requiredRoles().containsAll(insight.speakerRoles())) {
                throw invalidResponse("scenario insight에 시나리오와 맞지 않는 화자가 포함되어 있습니다.");
            }
            if (!LinerAnalysisPolicy.scenarioInsightCategories(source.scenario())
                    .contains(insight.category())) {
                throw invalidResponse("분석 시나리오에 맞지 않는 상황별 관찰입니다.");
            }
            requireText(insight.title(), "scenarioInsight.title");
            requireText(insight.description(), "scenarioInsight.description");
            validateEvidence(insight.evidenceSegmentIds(), segmentsById, "scenarioInsight");

            for (SpeakerRole speakerRole : insight.speakerRoles()) {
                boolean containsSpeakerEvidence = insight.evidenceSegmentIds().stream()
                        .map(segmentsById::get)
                        .anyMatch(segment -> segment.speakerRole() == speakerRole);
                if (!containsSpeakerEvidence) {
                    throw invalidResponse("상황별 관찰에는 관련 화자의 근거 발화가 필요합니다.");
                }
            }
        }
    }

    private Map<Long, AnalysisSegment> segmentsById(List<AnalysisSegment> segments) {
        Map<Long, AnalysisSegment> result = new HashMap<>();
        for (AnalysisSegment segment : segments) {
            result.put(segment.segmentId(), segment);
        }
        return result;
    }

    private void validateEvidence(
            List<Long> evidenceSegmentIds,
            Map<Long, AnalysisSegment> segmentsById,
            String fieldName
    ) {
        if (evidenceSegmentIds == null || evidenceSegmentIds.isEmpty()) {
            throw invalidResponse(fieldName + "의 근거 발화는 비어 있을 수 없습니다.");
        }
        if (new HashSet<>(evidenceSegmentIds).size() != evidenceSegmentIds.size()) {
            throw invalidResponse(fieldName + "의 근거 발화는 중복될 수 없습니다.");
        }
        if (evidenceSegmentIds.stream().anyMatch(id -> id == null || !segmentsById.containsKey(id))) {
            throw invalidResponse(fieldName + "에 존재하지 않는 근거 발화가 포함되어 있습니다.");
        }
    }

    private void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw invalidResponse(fieldName + "은(는) 비어 있을 수 없습니다.");
        }
    }

    private LinerAnalysisException invalidResponse(String message) {
        return new LinerAnalysisException("invalid_analysis_response", message, null);
    }
}
