package com.example.resay.domain.user.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateAgreementsRequestDto(
        @NotNull(message = "마케팅 수신 동의 여부를 보내 주세요.")
        Boolean marketing
) {
}
