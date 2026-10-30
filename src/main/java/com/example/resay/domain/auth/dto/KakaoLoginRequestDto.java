package com.example.resay.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record KakaoLoginRequestDto(
        @NotBlank(message = "인가 코드를 입력해 주세요.")
        String code,

        // 카카오는 토큰 교환 시 authorize 때 사용한 값과 같은지 확인하므로 프론트가 함께 보낸다
        @NotBlank(message = "redirectUri를 입력해 주세요.")
        String redirectUri
) {
}
