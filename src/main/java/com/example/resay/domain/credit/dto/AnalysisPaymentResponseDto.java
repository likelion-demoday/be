package com.example.resay.domain.credit.dto;

public record AnalysisPaymentResponseDto(
        Long recordingId,
        // 이 녹음의 분석에 차감된 크레딧
        int usedCredits,
        // 결제 후 잔액
        int balance,
        // 이미 결제된 녹음이라 이번 요청으로는 차감하지 않았는지 (결제 버튼을 연달아 누른 경우 등)
        boolean alreadyPaid
) {
}
