package com.example.resay.domain.recording.dto;

import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RelationshipType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "대화 보관함 항목")
public record RecordingListItemDto(
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
        @Schema(description = "음성을 다시 들을 수 있는지. 분석이 끝나고 3일이 지나면 음성이 삭제되어 false", example = "true")
        boolean audioAvailable
) {

    public static RecordingListItemDto from(Recording recording) {
        return new RecordingListItemDto(
                recording.getId(),
                recording.getTitle(),
                recording.getRelationshipType(),
                recording.getDurationSeconds(),
                recording.getCreatedAt(),
                recording.hasAudio()
        );
    }
}
