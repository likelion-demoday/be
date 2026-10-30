package com.example.resay.domain.character.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "캐릭터 이미지 생성 요청 결과")
public record CharacterImageRequestResponseDto(
        @Schema(description = "이미지를 생성할 녹음 ID", example = "1")
        Long recordingId,
        @Schema(description = "이미 요청되었거나 생성된 작업인지 여부", example = "false")
        boolean alreadyRequested
) {
}
