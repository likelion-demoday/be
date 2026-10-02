package com.example.resay.domain.analysis.dto;

import com.example.resay.domain.analysis.entity.AnalysisStatus;
import tools.jackson.databind.JsonNode;

public record AnalysisReportResponseDto(
        Long recordingId,
        AnalysisStatus status,
        JsonNode report,
        String modelName,
        String promptVersion,
        String schemaVersion
) {
}
