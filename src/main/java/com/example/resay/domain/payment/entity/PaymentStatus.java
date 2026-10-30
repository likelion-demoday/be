package com.example.resay.domain.payment.entity;

public enum PaymentStatus {
    // 주문만 만든 상태. 결제창에서 인증을 마치면 APPROVING으로 넘어간다
    READY,
    // 나이스페이에 승인을 요청한 상태. 결제됐는지 아직 확정되지 않았다
    APPROVING,
    // 결제됐고 크레딧이 지급됐다
    PAID,
    // 결제되지 않고 끝났다 (인증 실패 · 취소, 승인 거절, 망취소)
    FAILED,
    // 결제하지 않은 채 시간이 지났다
    EXPIRED,
    // 환불(결제 취소)을 요청한 상태. 크레딧은 이미 회수했고, 나이스페이에서 취소됐는지 아직 확정되지 않았다
    CANCELLING,
    // 결제가 취소됐다 (환불). 지급했던 크레딧은 회수했다
    CANCELLED;

    /** 결제가 확정된 적이 있는 주문인지 (지금은 환불 중이거나 환불됐더라도). */
    public boolean hasBeenPaid() {
        return this == PAID || this == CANCELLING || this == CANCELLED;
    }
}
