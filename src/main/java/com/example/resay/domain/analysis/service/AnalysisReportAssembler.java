package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.ConversationMetrics;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerMetrics;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
// 정량적 분석과 정성적 분석을 합침
@Component
public class AnalysisReportAssembler {

    private static final String REPORT_SCHEMA_VERSION = "analysis-report-v6";

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

        AnalysisReport report = new AnalysisReport(
                recordingInfo(source),
                quantitativeAnalysis(source, metrics),
                qualitativeAnalysis(source, qualitativeResult.resultJson())
        );

        return new AnalysisModelResult(
                writeJson(report),
                qualitativeResult.modelName(),
                qualitativeResult.promptVersion(),
                REPORT_SCHEMA_VERSION
        );
    }

    private AnalysisReport.RecordingInfo recordingInfo(AnalysisSource source) {
        return new AnalysisReport.RecordingInfo(
                source.recordingId(),
                source.scenario(),
                source.durationMs(),
                source.speakers()
        );
    }

    private AnalysisReport.QuantitativeAnalysis quantitativeAnalysis(
            AnalysisSource source,
            ConversationMetrics metrics
    ) {
        LinkedHashSet<SpeakerRole> roles = new LinkedHashSet<>();
        source.segments().forEach(segment -> roles.add(segment.speakerRole()));
        List<SpeakerMetrics> speakers = roles.stream().map(metrics::metricsFor).toList();

        return new AnalysisReport.QuantitativeAnalysis(
                speakers,
                metrics.speakingSpeedComparison().orElse(null)
        );
    }

    private AnalysisReport.QualitativeReport qualitativeAnalysis(
            AnalysisSource source,
            String resultJson
    ) {
        QualitativeAnalysis result = readQualitativeAnalysis(resultJson);
        return new AnalysisReport.QualitativeReport(
                result.overview(),
                enrichTimeline(result.timeline(), source.segments()),
                enrichTopics(result.topics(), source.segments()),
                result.characterInsights(),
                result.speakerInsights(),
                result.interestInsights(),
                result.spicinessInsights(),
                result.reactionStyleInsights(),
                result.scenarioInsights()
        );
    }

    private List<AnalysisReport.TimelineItem> enrichTimeline(
            List<QualitativeAnalysis.TimelineItem> items,
            List<AnalysisSegment> segments
    ) {
        Map<Long, AnalysisSegment> segmentsById = new java.util.LinkedHashMap<>();
        segments.forEach(segment -> segmentsById.put(segment.segmentId(), segment));

        return items.stream()
                .map(item -> enrichTimelineItem(item, segmentsById))
                .toList();
    }

    private AnalysisReport.TimelineItem enrichTimelineItem(
            QualitativeAnalysis.TimelineItem item,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        List<Long> evidenceIds = item.evidenceSegmentIds();
        if (evidenceIds.isEmpty()) {
            throw new IllegalStateException("대화 타임라인의 근거 발화가 비어 있습니다.");
        }

        List<AnalysisSegment> evidenceSegments = evidenceIds.stream()
                .map(segmentsById::get)
                .toList();
        if (evidenceSegments.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalStateException("대화 타임라인에 존재하지 않는 근거 발화가 포함되어 있습니다.");
        }

        long startMs = evidenceSegments.stream().mapToLong(AnalysisSegment::startMs).min().orElseThrow();
        long endMs = evidenceSegments.stream().mapToLong(AnalysisSegment::endMs).max().orElseThrow();
        return new AnalysisReport.TimelineItem(
                item.title(),
                item.description(),
                item.evidenceSegmentIds(),
                startMs,
                endMs
        );
    }

    private List<AnalysisReport.TopicItem> enrichTopics(
            List<QualitativeAnalysis.Topic> topics,
            List<AnalysisSegment> segments
    ) {
        Map<Long, AnalysisSegment> segmentsById = new java.util.LinkedHashMap<>();
        segments.forEach(segment -> segmentsById.put(segment.segmentId(), segment));

        List<AnalysisReport.TopicItem> enriched = topics.stream()
                .map(topic -> enrichTopic(topic, segmentsById))
                .toList();
        long longestDuration = enriched.stream()
                .mapToLong(AnalysisReport.TopicItem::speakingDurationMs)
                .max()
                .orElseThrow();

        return enriched.stream()
                .map(topic -> new AnalysisReport.TopicItem(
                        topic.title(),
                        topic.description(),
                        topic.segmentIds(),
                        topic.timeRanges(),
                        topic.turnCount(),
                        topic.speakingDurationMs(),
                        topic.speakingDurationMs() == longestDuration
                ))
                .toList();
    }

    private AnalysisReport.TopicItem enrichTopic(
            QualitativeAnalysis.Topic topic,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        Map<Long, Integer> positionsById = new java.util.HashMap<>();
        int position = 0;
        for (Long segmentId : segmentsById.keySet()) {
            positionsById.put(segmentId, position++);
        }
        List<AnalysisSegment> resolvedSegments = topic.segmentIds().stream()
                .map(segmentsById::get)
                .toList();
        if (resolvedSegments.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalStateException("주제에 존재하지 않는 발화가 포함되어 있습니다.");
        }
        List<AnalysisSegment> topicSegments = resolvedSegments.stream()
                .sorted(Comparator.comparingInt(segment -> positionsById.get(segment.segmentId())))
                .toList();

        long speakingDurationMs = topicSegments.stream()
                .mapToLong(segment -> segment.endMs() - segment.startMs())
                .sum();

        int turnCount = 0;
        SpeakerRole previousRole = null;
        Integer previousPosition = null;
        for (AnalysisSegment segment : topicSegments) {
            int currentPosition = positionsById.get(segment.segmentId());
            if (previousPosition == null
                    || currentPosition != previousPosition + 1
                    || segment.speakerRole() != previousRole) {
                turnCount++;
            }
            previousRole = segment.speakerRole();
            previousPosition = currentPosition;
        }

        return new AnalysisReport.TopicItem(
                topic.title(),
                topic.description(),
                topic.segmentIds(),
                topicTimeRanges(topicSegments, positionsById),
                turnCount,
                speakingDurationMs,
                false
        );
    }

    private List<AnalysisReport.TopicTimeRange> topicTimeRanges(
            List<AnalysisSegment> topicSegments,
            Map<Long, Integer> positionsById
    ) {
        List<AnalysisReport.TopicTimeRange> result = new java.util.ArrayList<>();
        Long rangeStartMs = null;
        Long rangeEndMs = null;
        Integer previousPosition = null;

        for (AnalysisSegment segment : topicSegments) {
            int currentPosition = positionsById.get(segment.segmentId());
            if (previousPosition == null || currentPosition != previousPosition + 1) {
                if (rangeStartMs != null) {
                    result.add(new AnalysisReport.TopicTimeRange(rangeStartMs, rangeEndMs));
                }
                rangeStartMs = segment.startMs();
            }
            rangeEndMs = segment.endMs();
            previousPosition = currentPosition;
        }
        if (rangeStartMs != null) {
            result.add(new AnalysisReport.TopicTimeRange(rangeStartMs, rangeEndMs));
        }
        return List.copyOf(result);
    }

    private QualitativeAnalysis readQualitativeAnalysis(String json) {
        try {
            return objectMapper.readValue(json, QualitativeAnalysis.class);
        } catch (Exception exception) {
            throw new IllegalStateException("정성 분석 결과를 보고서 형식으로 변환할 수 없습니다.", exception);
        }
    }

    private String writeJson(AnalysisReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception exception) {
            throw new IllegalStateException("분석 보고서를 JSON으로 변환할 수 없습니다.", exception);
        }
    }
}
