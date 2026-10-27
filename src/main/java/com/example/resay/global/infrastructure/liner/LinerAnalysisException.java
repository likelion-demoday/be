package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.service.AnalysisFailureCategory;
import com.example.resay.domain.analysis.service.AnalysisProcessingException;
import lombok.Getter;

@Getter
public class LinerAnalysisException extends AnalysisProcessingException {

    private final String errorCode;

    public LinerAnalysisException(String errorCode, String message, Throwable cause) {
        this(defaultCategory(errorCode), errorCode, message, cause);
    }

    public LinerAnalysisException(
            AnalysisFailureCategory category,
            String errorCode,
            String message,
            Throwable cause
    ) {
        super(category, message, cause);
        this.errorCode = errorCode;
    }

    private static AnalysisFailureCategory defaultCategory(String errorCode) {
        if ("invalid_analysis_response".equals(errorCode)) {
            return AnalysisFailureCategory.RESPONSE_VALIDATION_ERROR;
        }
        return AnalysisFailureCategory.UNEXPECTED_ERROR;
    }
}
