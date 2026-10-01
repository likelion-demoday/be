package com.example.resay.domain.analysis.model;

import org.springframework.util.StringUtils;

public record AnalysisModelResult(
        String resultJson,
        String modelName,
        String promptVersion,
        String schemaVersion
) {

    public AnalysisModelResult {
        requireText(resultJson, "resultJson");
        requireText(modelName, "modelName");
        requireText(promptVersion, "promptVersion");
        requireText(schemaVersion, "schemaVersion");
    }

    private static void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(fieldName + "은(는) 비어 있을 수 없습니다.");
        }
    }
}
