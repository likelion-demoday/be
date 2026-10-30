package com.example.resay.domain.analysis.dto;

public record AnalysisResultCommand(
        String resultJson,
        String modelName,
        String promptVersion,
        String schemaVersion
) {
}
