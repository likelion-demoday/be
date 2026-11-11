package com.example.resay.domain.credit.dto;

public record CreditSummaryResponseDto(
        int balance,
        // 캐릭터 생성 무료 기회가 남아 있는지 (계정당 1회)
        boolean characterFreeAvailable,
        // 분석 무료 기회가 남아 있는지 (카카오 · 구글 가입 계정당 1회, 유형 무관). true면 다음 분석은 크레딧을 차감하지 않는다
        boolean analysisFreeAvailable
) {
}
