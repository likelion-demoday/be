package com.example.resay.domain.character.dto;

import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.character.entity.CharacterImageStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "캐릭터 이미지 생성 상태 목록")
public record CharacterImageListResponseDto(
        @Schema(description = "녹음 ID", example = "1")
        Long recordingId,
        @Schema(description = "화자별 캐릭터 이미지 상태")
        List<CharacterImageItem> images
) {

    @Schema(description = "화자별 캐릭터 이미지 상태")
    public record CharacterImageItem(
            @Schema(description = "화자 역할", example = "SELF")
            SpeakerRole speakerRole,
            @Schema(description = "생성 상태", example = "COMPLETED")
            CharacterImageStatus status,
            @Schema(
                    description = "완료된 이미지 조회 주소. 생성 전에는 null",
                    example = "/api/v1/analyses/1/character-images/SELF/content",
                    nullable = true
            )
            String contentUrl
    ) {
    }
}
