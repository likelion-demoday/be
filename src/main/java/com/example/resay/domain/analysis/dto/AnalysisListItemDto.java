package com.example.resay.domain.analysis.dto;

import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import com.example.resay.domain.analysis.entity.AnalysisStatus;
import com.example.resay.domain.recording.entity.RelationshipType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "분석 목록 항목")
public record AnalysisListItemDto(
        @Schema(description = "녹음 ID", example = "1")
        Long recordingId,
        @Schema(description = "녹음 제목", example = "친구와의 대화 - 10월 6일")
        String title,
        @Schema(description = "관계 및 대화 상황", example = "FRIEND_DAILY")
        RelationshipType relationshipType,
        @Schema(description = "녹음 길이(초)", example = "600")
        Integer durationSeconds,
        @Schema(description = "녹음 데이터 생성 시각")
        LocalDateTime createdAt,
        @Schema(description = "분석 상태", example = "COMPLETED")
        AnalysisStatus analysisStatus,
        @Schema(description = "분석 실패 사유. 실패 상태가 아니면 null")
        AnalysisFailureReason failureReason
) {
}
