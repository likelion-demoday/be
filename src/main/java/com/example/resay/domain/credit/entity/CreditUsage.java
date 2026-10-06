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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 크레딧을 사용한 건 (분석 1건, 캐릭터 생성 1건).
 * 실패해서 환급되면 지우지 않고 REFUNDED로 남긴다. 환급된 뒤에는 같은 녹음에 새 사용 건을 만들 수 있다.
 * 이 엔티티는 테이블 정의와 조회에만 쓴다. 추가 · 환급 처리는 CreditJdbcRepository가 SQL로 직접 한다.
 */
@Getter
@Entity
@Table(
        name = "credit_usages",
        uniqueConstraints = @UniqueConstraint(name = "uk_credit_usages_active_key", columnNames = "active_key"),
        indexes = {
                @Index(name = "idx_credit_usages_user_id", columnList = "user_id"),
                @Index(name = "idx_credit_usages_recording_id", columnList = "recording_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditUsage extends BaseEntity {

    public static final int REFUND_REASON_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, columnDefinition = "varchar(20)")
    private UsagePurpose purpose;

    @Column(name = "recording_id", nullable = false, updatable = false)
    private Long recordingId;

    // 차감한 크레딧. 무료 기회를 쓴 건은 0
    @Column(nullable = false, updatable = false)
    private int amount;

    @Column(name = "free_use", nullable = false, updatable = false)
    private boolean freeUse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private UsageStatus status;

    // "같은 녹음 · 같은 용도에 유효한(USED) 사용 건은 하나뿐"을 DB가 보장하게 하는 값.
    // USED인 동안에만 값이 있고 환급되면 null이 된다. 유니크 제약은 null끼리는 겹친다고 보지 않으므로
    // 환급된 건은 여러 개 쌓일 수 있고, 유효한 건은 하나만 존재할 수 있다.
    @Column(name = "active_key", length = 50)
    private String activeKey;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;

    @Column(name = "refund_reason", length = REFUND_REASON_MAX_LENGTH)
    private String refundReason;

    public static String activeKey(UsagePurpose purpose, Long recordingId) {
        return purpose.name() + ":" + recordingId;
    }
}
