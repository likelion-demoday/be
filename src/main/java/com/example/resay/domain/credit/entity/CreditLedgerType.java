package com.example.resay.domain.credit.entity;

public enum CreditLedgerType {
    // 결제로 충전
    CHARGE,
    // 분석 · 캐릭터 생성에 사용
    USE,
    // 사용한 건이 실패해서 되돌려 줌
    USE_REFUND,
    // 운영자가 직접 더하거나 뺌
    ADJUST,
    // 충전을 환불(결제 취소)하면서 회수함
    CHARGE_REFUND,
    // 환불이 되지 않아(결제 취소 거절) 회수한 크레딧을 되돌려 줌
    CHARGE_REFUND_UNDO
}
