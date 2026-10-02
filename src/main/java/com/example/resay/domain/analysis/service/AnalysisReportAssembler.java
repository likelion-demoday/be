package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.ConversationMetrics;
import com.example.resay.domain.analysis.model.SpeakerMetrics;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
// 정량적 분석과 정성적 분석을 합침
@Component
public class AnalysisReportAssembler {

    private static final String REPORT_SCHEMA_VERSION = "analysis-report-v1";

    private final ObjectMapper objectMapper;

    public AnalysisReportAssembler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnalysisModelResult assemble(
            AnalysisSource source,
            ConversationMetrics metrics,
            AnalysisModelResult qualitativeResult
    ) {
        if (source == null || metrics == null || qualitativeResult == null) {
            throw new IllegalArgumentException("보고서 생성 입력은 비어 있을 수 없습니다.");
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("recordingInfo", recordingInfo(source));
        report.put("quantitativeAnalysis", quantitativeAnalysis(source, metrics));
        report.put("qualitativeAnalysis", qualitativeAnalysis(source, qualitativeResult.resultJson()));

        return new AnalysisModelResult(
                writeJson(report),
                qualitativeResult.modelName(),
                qualitativeResult.promptVersion(),
                REPORT_SCHEMA_VERSION
        );
    }

    private Map<String, Object> recordingInfo(AnalysisSource source) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recordingId", source.recordingId());
        result.put("scenario", source.scenario());
        result.put("durationMs", source.durationMs());
        return result;
    }

    private Map<String, Object> quantitativeAnalysis(
            AnalysisSource source,
            ConversationMetrics metrics
    ) {
        LinkedHashSet<SpeakerRole> roles = new LinkedHashSet<>();
        source.segments().forEach(segment -> roles.add(segment.speakerRole()));
        List<SpeakerMetrics> speakers = roles.stream().map(metrics::metricsFor).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("speakers", speakers);
        result.put("speakingSpeedComparison", metrics.speakingSpeedComparison().orElse(null));
        return result;
    }

    private Map<String, Object> qualitativeAnalysis(AnalysisSource source, String resultJson) {
        Map<String, Object> result = readJsonObject(resultJson);
        Object timeline = result.get("timeline");
        if (timeline instanceof List<?> items) {
            result.put("timeline", enrichTimeline(items, source.segments()));
        }
        return result;
    }

    private List<Map<String, Object>> enrichTimeline(
            List<?> items,
            List<AnalysisSegment> segments
    ) {
        Map<Long, AnalysisSegment> segmentsById = new LinkedHashMap<>();
        segments.forEach(segment -> segmentsById.put(segment.segmentId(), segment));

        return items.stream()
                .map(item -> enrichTimelineItem(item, segmentsById))
                .toList();
    }

    private Map<String, Object> enrichTimelineItem(
            Object item,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        if (!(item instanceof Map<?, ?> rawItem)) {
            throw new IllegalStateException("대화 타임라인 형식이 올바르지 않습니다.");
        }

        Map<String, Object> timelineItem = stringKeyMap(rawItem);
        Object evidence = timelineItem.get("evidenceSegmentIds");
        if (!(evidence instanceof List<?> evidenceIds) || evidenceIds.isEmpty()) {
            throw new IllegalStateException("대화 타임라인의 근거 발화가 비어 있습니다.");
        }

        List<AnalysisSegment> evidenceSegments = evidenceIds.stream()
                .map(this::toLong)
                .map(segmentsById::get)
                .toList();
        if (evidenceSegments.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalStateException("대화 타임라인에 존재하지 않는 근거 발화가 포함되어 있습니다.");
        }

        long startMs = evidenceSegments.stream().mapToLong(AnalysisSegment::startMs).min().orElseThrow();
        long endMs = evidenceSegments.stream().mapToLong(AnalysisSegment::endMs).max().orElseThrow();
        timelineItem.put("startMs", startMs);
        timelineItem.put("endMs", endMs);
        return timelineItem;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("근거 발화 ID 형식이 올바르지 않습니다.");
    }

    private Map<String, Object> readJsonObject(String json) {
        try {
            Map<?, ?> raw = objectMapper.readValue(json, Map.class);
            return stringKeyMap(raw);
        } catch (Exception exception) {
            throw new IllegalStateException("정성 분석 결과를 보고서 형식으로 변환할 수 없습니다.", exception);
        }
    }

    private Map<String, Object> stringKeyMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private String writeJson(Map<String, Object> report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception exception) {
            throw new IllegalStateException("분석 보고서를 JSON으로 변환할 수 없습니다.", exception);
        }
    }
}
