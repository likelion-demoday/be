package com.example.resay.domain.analysis.dto;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import jakarta.validation.constraints.NotNull;

public record MockAnalysisRequestDto(
        @NotNull(message = "분석 시나리오는 필수입니다.")
        AnalysisScenario scenario
) {
}
