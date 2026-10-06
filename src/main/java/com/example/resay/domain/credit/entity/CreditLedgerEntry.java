package com.example.resay.domain.credit.entity;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 크레딧 장부. 잔액이 바뀔 때마다 한 줄씩 추가한다.
 * 추가만 하고 고치거나 지우지 않는다. 한 사용자의 amount 합계는 항상 그 사용자의 잔액과 같다.
 */
@Getter
@Entity
@Table(
        name = "credit_ledger",
        indexes = @Index(name = "idx_credit_ledger_user_id_id", columnList = "user_id, id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditLedgerEntry extends BaseEntity {

    public static final int MEMO_MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, columnDefinition = "varchar(20)")
    private CreditLedgerType type;

    // 잔액에 더해진 값 (늘면 양수, 줄면 음수)
    @Column(nullable = false, updatable = false)
    private int amount;

    @Column(name = "balance_after", nullable = false, updatable = false)
    private int balanceAfter;

    // 사용 · 환급이면 어떤 사용 건 때문인지
    @Column(name = "usage_id", updatable = false)
    private Long usageId;

    // 내역 화면에서 사용 건을 다시 조회하지 않도록 용도와 녹음을 함께 적어 둔다
    @Enumerated(EnumType.STRING)
    @Column(updatable = false, columnDefinition = "varchar(20)")
    private UsagePurpose purpose;

    @Column(name = "recording_id", updatable = false)
    private Long recordingId;

    // 운영자 조정 사유 (사용자에게는 보여주지 않는다)
    @Column(updatable = false, length = MEMO_MAX_LENGTH)
    private String memo;

    // 운영자 조정이면 처리한 운영자
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    private CreditLedgerEntry(Long userId, CreditLedgerType type, int amount, int balanceAfter) {
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
    }

    /** @param usedAmount 차감한 크레딧 (양수 또는 무료면 0). 장부에는 음수로 적힌다 */
    public static CreditLedgerEntry use(
            Long userId, Long usageId, UsagePurpose purpose, Long recordingId, int usedAmount, int balanceAfter
    ) {
        CreditLedgerEntry entry = new CreditLedgerEntry(userId, CreditLedgerType.USE, -usedAmount, balanceAfter);
        entry.linkToUsage(usageId, purpose, recordingId);
        return entry;
    }

    /** @param refundedAmount 되돌려 준 크레딧 (양수 또는 무료였다면 0) */
    public static CreditLedgerEntry useRefund(
            Long userId, Long usageId, UsagePurpose purpose, Long recordingId, int refundedAmount, int balanceAfter
    ) {
        CreditLedgerEntry entry =
                new CreditLedgerEntry(userId, CreditLedgerType.USE_REFUND, refundedAmount, balanceAfter);
        entry.linkToUsage(usageId, purpose, recordingId);
        return entry;
    }

    public static CreditLedgerEntry adjust(Long userId, int amount, int balanceAfter, String memo, Long adminId) {
        CreditLedgerEntry entry = new CreditLedgerEntry(userId, CreditLedgerType.ADJUST, amount, balanceAfter);
        entry.memo = memo;
        entry.createdBy = adminId;
        return entry;
    }

    private void linkToUsage(Long usageId, UsagePurpose purpose, Long recordingId) {
        this.usageId = usageId;
        this.purpose = purpose;
        this.recordingId = recordingId;
    }
}
