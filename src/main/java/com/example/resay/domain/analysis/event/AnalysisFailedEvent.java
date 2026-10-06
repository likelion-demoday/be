package com.example.resay.domain.analysis.event;

import com.example.resay.domain.analysis.entity.AnalysisFailureReason;

public record AnalysisFailedEvent(
        Long recordingId,
        AnalysisFailureReason failureReason
) {

    public AnalysisFailedEvent {
        if (recordingId == null || recordingId <= 0) {
            throw new IllegalArgumentException("recordingId는 양수여야 합니다.");
        }
        if (failureReason == null) {
            throw new IllegalArgumentException("failureReason은 비어 있을 수 없습니다.");
        }
    }
}
