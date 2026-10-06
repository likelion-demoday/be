package com.example.resay.domain.credit.entity;

public enum CreditLedgerType {
    // 분석 · 캐릭터 생성에 사용
    USE,
    // 사용한 건이 실패해서 되돌려 줌
    USE_REFUND,
    // 운영자가 직접 더하거나 뺌
    ADJUST
}
