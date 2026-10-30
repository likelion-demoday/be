package com.example.resay.domain.analysis.service;

import java.util.Objects;
import lombok.Getter;

@Getter
public class AnalysisProcessingException extends RuntimeException {

    private final AnalysisFailureCategory category;

    public AnalysisProcessingException(
            AnalysisFailureCategory category,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.category = Objects.requireNonNull(category, "분석 실패 분류는 비어 있을 수 없습니다.");
    }
}
