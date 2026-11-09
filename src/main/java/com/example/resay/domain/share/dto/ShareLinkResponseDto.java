package com.example.resay.domain.share.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "분석 보고서 공유 링크 발급 응답")
public record ShareLinkResponseDto(
        @Schema(description = "프론트 공유 페이지 주소. 토큰은 URL fragment에 포함됩니다.")
        String shareUrl,
        @Schema(description = "공유 링크 만료 시각")
        LocalDateTime expiresAt
) {
}
