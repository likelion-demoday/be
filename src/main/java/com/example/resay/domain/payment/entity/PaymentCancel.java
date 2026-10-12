package com.example.resay.domain.payment.entity;

import com.example.resay.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 충전 주문의 환불(결제 취소) 기록. 취소를 시도할 때마다 한 줄씩 생긴다 (거절돼서 다시 요청하면 두 줄).
 *
 * 지금은 전액 취소만 하지만, 일부만 환불하는 경우를 나중에 붙일 수 있게 환불 금액과 회수한 크레딧을 줄마다 적어 둔다.
 * 상태는 Payment와 마찬가지로 PaymentCancelRepository의 "지금 상태가 맞을 때만 바꾸는" UPDATE로만 바꾼다.
 */
@Getter
@Entity
@Table(
        name = "payment_cancels",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_cancels_cancel_order_id", columnNames = "cancel_order_id"),
        indexes = {
                @Index(name = "idx_payment_cancels_payment_id", columnList = "payment_id"),
                @Index(name = "idx_payment_cancels_status_id", columnList = "status, id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCancel extends BaseEntity {

    public static final int REASON_MAX_LENGTH = 200;
    public static final int FAIL_CODE_MAX_LENGTH = 30;
    public static final int FAIL_MESSAGE_MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    // 나이스페이 취소 요청에 넣는 번호. 취소 요청마다 새로 만든다 (원래 주문번호가 아니다).
    // 우리가 요청하지 않은 취소(나이스페이 관리자 화면에서 한 취소)에는 없다
    @Column(name = "cancel_order_id", updatable = false, length = 64)
    private String cancelOrderId;

    // 환불 금액(원)
    @Column(nullable = false, updatable = false)
    private int amount;

    // 회수한 크레딧. 보통 주문의 지급 크레딧과 같고, 이미 환불된 결제의 크레딧을 뒤늦게 회수할 때만 더 적을 수 있다
    @Column(name = "credit_amount", nullable = false)
    private int creditAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private PaymentCancelStatus status;

    // 운영자가 적은 환불 사유 (사용자에게도 나이스페이에도 보내지 않는다)
    @Column(length = REASON_MAX_LENGTH)
    private String reason;

    // 환불을 처리한 운영자. null이면 우리 API를 거치지 않고 나이스페이 쪽에서 취소된 결제다
    @Column(name = "requested_by", updatable = false)
    private Long requestedBy;

    // 취소가 되지 않은 이유. 나이스페이가 준 코드(4자) · 문구, 또는 우리가 붙이는 코드
    @Column(name = "fail_code", length = FAIL_CODE_MAX_LENGTH)
    private String failCode;

    @Column(name = "fail_message", length = FAIL_MESSAGE_MAX_LENGTH)
    private String failMessage;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    /** 운영자가 요청한 환불. 크레딧을 회수하는 트랜잭션에서 만든다. */
    public static PaymentCancel request(Payment payment, String cancelOrderId, String reason, Long adminId) {
        PaymentCancel cancel = of(payment, PaymentCancelStatus.REQUESTED, reason);
        cancel.cancelOrderId = cancelOrderId;
        cancel.creditAmount = payment.getCreditAmount();
        cancel.requestedBy = adminId;
        return cancel;
    }

    /** 나이스페이 쪽에서 이미 취소된 결제를 알게 됐을 때 남기는 기록. 회수한 크레딧은 회수한 뒤에 적는다. */
    public static PaymentCancel alreadyCancelled(Payment payment, String reason, LocalDateTime cancelledAt) {
        PaymentCancel cancel = of(payment, PaymentCancelStatus.DONE, reason);
        cancel.cancelledAt = cancelledAt;
        return cancel;
    }

    /** 방금 만든 기록에만 쓴다 (아직 커밋되지 않아 다른 요청이 볼 수 없는 동안). */
    public void recordCollectedCredits(int collected) {
        this.creditAmount = collected;
    }

    private static PaymentCancel of(Payment payment, PaymentCancelStatus status, String reason) {
        PaymentCancel cancel = new PaymentCancel();
        cancel.paymentId = payment.getId();
        cancel.userId = payment.getUserId();
        cancel.amount = payment.getAmount();
        cancel.status = status;
        cancel.reason = reason;
        return cancel;
    }
}
