package com.example.resay.domain.analysis.event;

public record AnalysisCompletedEvent(
        Long analysisId,
        Long recordingId
) {
}
