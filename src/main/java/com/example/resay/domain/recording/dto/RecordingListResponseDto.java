package com.example.resay.domain.recording.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

// 페이지 형식은 분석 목록(GET /api/v1/analyses)과 같게 맞춘다
@Schema(description = "대화 보관함 페이지")
public record RecordingListResponseDto(
        List<RecordingListItemDto> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public RecordingListResponseDto {
        items = List.copyOf(items);
    }
}
