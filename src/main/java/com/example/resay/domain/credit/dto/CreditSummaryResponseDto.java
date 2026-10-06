package com.example.resay.domain.credit.dto;

public record CreditSummaryResponseDto(
        int balance,
        // 캐릭터 생성 무료 기회가 남아 있는지 (계정당 1회)
        boolean characterFreeAvailable
) {
}
