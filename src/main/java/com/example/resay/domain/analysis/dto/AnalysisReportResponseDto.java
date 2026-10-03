package com.example.resay.domain.analysis.dto;

import com.example.resay.domain.analysis.entity.AnalysisStatus;

public record AnalysisReportResponseDto(
        Long recordingId,
        AnalysisStatus status,
        AnalysisReportDto report,
        String modelName,
        String promptVersion,
        String schemaVersion
) {
}
