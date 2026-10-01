package com.example.resay.domain.analysis.model;

import org.springframework.util.StringUtils;

// LINER에 전달할 한 발화 (세그먼트 한 개)
public record AnalysisSegment(
        Long segmentId,
        SpeakerRole speakerRole,
        Long startMs,
        Long endMs,
        String content
) {

    public AnalysisSegment {
        if (segmentId == null || segmentId <= 0) {
            throw new IllegalArgumentException("segmentId는 양수여야 합니다.");
        }
        if (speakerRole == null) {
            throw new IllegalArgumentException("speakerRole은 비어 있을 수 없습니다.");
        }
        if (startMs == null || startMs < 0) {
            throw new IllegalArgumentException("startMs는 0 이상이어야 합니다.");
        }
        if (endMs == null || endMs <= startMs) {
            throw new IllegalArgumentException("endMs는 startMs보다 커야 합니다.");
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("content는 비어 있을 수 없습니다.");
        }
    }
}
