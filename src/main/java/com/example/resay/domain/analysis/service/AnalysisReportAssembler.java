package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.ConversationMetrics;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerMetrics;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.LinkedHashSet;
import java.util.List;
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
                source.durationMs()
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
                result.speakerInsights(),
                result.scenarioInsights()
        );
    }

    private List<AnalysisReport.TimelineItem> enrichTimeline(
            List<QualitativeAnalysis.TimelineItem> items,
            List<AnalysisSegment> segments
    ) {
        java.util.Map<Long, AnalysisSegment> segmentsById = new java.util.LinkedHashMap<>();
        segments.forEach(segment -> segmentsById.put(segment.segmentId(), segment));

        return items.stream()
                .map(item -> enrichTimelineItem(item, segmentsById))
                .toList();
    }

    private AnalysisReport.TimelineItem enrichTimelineItem(
            QualitativeAnalysis.TimelineItem item,
            java.util.Map<Long, AnalysisSegment> segmentsById
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
