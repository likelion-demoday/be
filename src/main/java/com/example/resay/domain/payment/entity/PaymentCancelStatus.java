package com.example.resay.domain.payment.entity;

public enum PaymentCancelStatus {
    // 크레딧을 회수하고 나이스페이에 취소를 요청했다. 취소됐는지 아직 확정되지 않았다
    REQUESTED,
    // 결제가 취소됐다
    DONE,
    // 취소되지 않았다. 회수한 크레딧은 되돌렸다
    FAILED
}
