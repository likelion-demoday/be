package com.example.resay.domain.credit.dto;

public record CreditAdjustResponseDto(
        Long userId,
        // 조정 후 잔액
        int balance
) {
}
