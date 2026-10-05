package com.example.resay.domain.analysis.event;

public record AnalysisRequestedEvent(Long recordingId) {

    public AnalysisRequestedEvent {
        if (recordingId == null || recordingId <= 0) {
            throw new IllegalArgumentException("recordingId는 양수여야 합니다.");
        }
    }
}
