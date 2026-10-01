package com.example.resay.domain.analysis.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 녹음 하나의 전체 분석 입력
public record AnalysisSource(
        Long recordingId,
        AnalysisScenario scenario,
        Long durationMs,
        List<AnalysisSegment> segments
) {

    public AnalysisSource {
        if (recordingId == null || recordingId <= 0) {
            throw new IllegalArgumentException("recordingId는 양수여야 합니다.");
        }
        if (scenario == null) {
            throw new IllegalArgumentException("scenario는 비어 있을 수 없습니다.");
        }
        if (durationMs == null || durationMs <= 0) {
            throw new IllegalArgumentException("durationMs는 양수여야 합니다.");
        }
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("segments는 비어 있을 수 없습니다.");
        }

        segments = List.copyOf(segments);
        validateSegmentIds(segments);
        validateSegmentOrder(segments);
        validateSpeakerRoles(scenario, segments);
    }

    private static void validateSegmentIds(List<AnalysisSegment> segments) {
        Set<Long> segmentIds = new HashSet<>();
        for (AnalysisSegment segment : segments) {
            if (!segmentIds.add(segment.segmentId())) {
                throw new IllegalArgumentException("segmentId는 중복될 수 없습니다.");
            }
        }
    }

    private static void validateSegmentOrder(List<AnalysisSegment> segments) {
        long previousStartMs = -1;
        for (AnalysisSegment segment : segments) {
            if (segment.startMs() < previousStartMs) {
                throw new IllegalArgumentException("segments는 시작 시각 순서여야 합니다.");
            }
            previousStartMs = segment.startMs();
        }
    }

    private static void validateSpeakerRoles(
            AnalysisScenario scenario,
            List<AnalysisSegment> segments
    ) {
        Set<SpeakerRole> actualRoles = new HashSet<>();
        for (AnalysisSegment segment : segments) {
            actualRoles.add(segment.speakerRole());
        }
        if (!actualRoles.equals(scenario.requiredRoles())) {
            throw new IllegalArgumentException("시나리오에 필요한 화자 역할과 일치하지 않습니다.");
        }
    }
}
