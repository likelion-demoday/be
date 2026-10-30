package com.example.resay.domain.credit.model;

/**
 * @param amount      차감한 크레딧 (무료였다면 0)
 * @param freeUse     무료 기회를 쓴 건인지
 * @param balance     처리 후 잔액
 * @param alreadyUsed 이미 차감된 대상이라 이번에는 차감하지 않았는지
 */
public record UsageResult(
        Long usageId,
        int amount,
        boolean freeUse,
        int balance,
        boolean alreadyUsed
) {
}
