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
 * 크레딧 충전 주문 (결제 시도 1번 = 주문 1건).
 * 카드 번호 같은 결제 수단 정보는 저장하지 않는다.
 *
 * 상태는 여러 요청이 동시에 바꾸려 할 수 있어(같은 인증 결과가 두 번 도착, 정리 작업과 겹침)
 * 엔티티를 고쳐 저장하지 않고 PaymentRepository의 "지금 상태가 맞을 때만 바꾸는" UPDATE로만 바꾼다.
 */
@Getter
@Entity
@Table(
        name = "payments",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payments_order_id", columnNames = "order_id"),
                // 인증 결과 하나를 두 주문에 쓰지 못하게 한다 (tid도, 인증 토큰도 한 주문에만)
                @UniqueConstraint(name = "uk_payments_tid", columnNames = "tid"),
                @UniqueConstraint(name = "uk_payments_auth_token_hash", columnNames = "auth_token_hash")
        },
        indexes = {
                @Index(name = "idx_payments_user_id_id", columnList = "user_id, id"),
                @Index(name = "idx_payments_status_id", columnList = "status, id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    public static final int FAIL_CODE_MAX_LENGTH = 30;
    public static final int FAIL_MESSAGE_MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    // 우리가 만드는 주문번호. 나이스페이에 그대로 전달되고, 로그인 없이 받는 인증 결과에서 주문을 찾는 열쇠라 추측할 수 없게 만든다
    @Column(name = "order_id", nullable = false, updatable = false, length = 64)
    private String orderId;

    // 주문 시점의 상품 · 금액 · 지급 크레딧. 나중에 가격이 바뀌어도 기록은 그대로 남는다
    @Column(name = "product_code", nullable = false, updatable = false, length = 30)
    private String productCode;

    @Column(nullable = false, updatable = false)
    private int amount;

    @Column(name = "credit_amount", nullable = false, updatable = false)
    private int creditAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private PaymentStatus status;

    // 나이스페이 거래 키. 인증을 마치고 승인을 요청할 때 채운다
    @Column(length = 30)
    private String tid;

    // 승인을 요청할 때 쓴 인증 토큰의 SHA-256. 토큰 자체는 저장하지 않는다
    @Column(name = "auth_token_hash", length = 64)
    private String authTokenHash;

    @Column(name = "approval_requested_at")
    private LocalDateTime approvalRequestedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "receipt_url", length = 200)
    private String receiptUrl;

    @Column(name = "fail_code", length = FAIL_CODE_MAX_LENGTH)
    private String failCode;

    @Column(name = "fail_message", length = FAIL_MESSAGE_MAX_LENGTH)
    private String failMessage;

    // 결제가 끝난 뒤 사용자를 돌려보낼 프론트 주소 (주문을 만들 때 허용 목록으로 확인한 출처)
    @Column(name = "result_origin", nullable = false, updatable = false, length = 200)
    private String resultOrigin;

    public static Payment create(
            Long userId, String orderId, String productCode, int amount, int creditAmount, String resultOrigin
    ) {
        Payment payment = new Payment();
        payment.userId = userId;
        payment.orderId = orderId;
        payment.productCode = productCode;
        payment.amount = amount;
        payment.creditAmount = creditAmount;
        payment.status = PaymentStatus.READY;
        payment.resultOrigin = resultOrigin;
        return payment;
    }
}
