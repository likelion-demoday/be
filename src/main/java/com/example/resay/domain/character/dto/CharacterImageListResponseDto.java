package com.example.resay.domain.character.dto;

import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.character.entity.CharacterImageStatus;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
                    description = "완료된 캐릭터 이미지 파일의 상대 URL. 생성 전에는 null",
                    example = "/api/v1/analyses/1/character-images/SELF/content",
                    nullable = true
            )
            String contentUrl,
            @Schema(
                    description = "카드에 표시할 캐릭터 이름",
                    example = "질문 수집가"
            )
            String characterName,
            @Schema(
                    description = "카드에 표시할 캐릭터 설명",
                    example = "꼬리 질문으로 상대의 이야기를 자연스럽게 이어 가요."
            )
            String characterDescription,
            @Schema(
                    description = "캐릭터 이름 글자색",
                    example = "#6D3A2A",
                    pattern = "^#[0-9A-F]{6}$"
            )
            String characterNameTextColor,
            @ArraySchema(
                    minItems = 2,
                    maxItems = 2,
                    schema = @Schema(
                            description = "카드 배경 그라데이션 색상",
                            example = "#FFF5EE",
                            pattern = "^#[0-9A-F]{6}$"
                    )
            )
            List<String> cardBackgroundGradientColors
    ) {
    }
}
