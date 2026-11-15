package com.example.resay.domain.recording.dto;

import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RelationshipType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "대화 보관함 상세")
public record RecordingDetailResponseDto(
        @Schema(description = "녹음 ID", example = "1")
        Long recordingId,
        @Schema(description = "녹음 제목", example = "친구와의 대화 - 10월 6일")
        String title,
        @Schema(description = "관계 및 대화 상황", example = "FRIEND_DAILY")
        RelationshipType relationshipType,
        @Schema(description = "녹음 길이(초)", example = "783")
        Integer durationSeconds,
        @Schema(description = "녹음을 올린 시각")
        LocalDateTime createdAt,
        @Schema(description = "분석 종료 후 원본 음성을 삭제하므로 항상 false", example = "false")
        boolean audioAvailable,
        @Schema(description = "분석이 끝난 녹음은 원본을 삭제하므로 항상 null", nullable = true)
        String audioUrl
) {

    public static RecordingDetailResponseDto from(Recording recording) {
        return new RecordingDetailResponseDto(
                recording.getId(),
                recording.getTitle(),
                recording.getRelationshipType(),
                recording.getDurationSeconds(),
                recording.getCreatedAt(),
                false,
                null
        );
    }
}
