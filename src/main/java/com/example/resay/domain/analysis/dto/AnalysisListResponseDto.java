package com.example.resay.domain.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "분석 목록 페이지")
public record AnalysisListResponseDto(
        List<AnalysisListItemDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public AnalysisListResponseDto {
        items = List.copyOf(items);
    }
}
