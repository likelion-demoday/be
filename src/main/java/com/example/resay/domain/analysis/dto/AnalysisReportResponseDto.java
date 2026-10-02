package com.example.resay.domain.analysis.dto;

import com.example.resay.domain.analysis.entity.AnalysisStatus;
import com.example.resay.domain.analysis.model.AnalysisReport;

public record AnalysisReportResponseDto(
        Long recordingId,
        AnalysisStatus status,
        AnalysisReport report,
        String modelName,
        String promptVersion,
        String schemaVersion
) {
}
