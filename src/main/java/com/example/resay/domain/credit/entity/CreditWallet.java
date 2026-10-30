package com.example.resay.domain.credit.entity;

import com.example.resay.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자별 크레딧 잔액 (사용자당 1행, 사용자 ID가 기본키).
 * 이 엔티티는 테이블 정의와 조회에만 쓴다. 잔액을 바꾸는 일은 CreditJdbcRepository가 행을 잠그고 SQL로 직접 한다.
 */
@Getter
@Entity
@Table(name = "credit_wallets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CreditWallet extends BaseEntity {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false)
    private int balance;

    // 캐릭터 생성 무료 기회(계정당 1회)를 쓴 사용 건. 기회가 남아 있으면 null이다.
    // 그 사용 건이 실패로 환급되면 다시 null로 돌아가 기회가 되살아난다.
    // 잔액과 같은 행에 두는 이유: 지갑을 잠글 때 함께 읽혀서, 동시에 요청해도 무료가 두 번 성립하지 않는다
    @Column(name = "character_free_usage_id")
    private Long characterFreeUsageId;

    public boolean hasCharacterFreeChance() {
        return characterFreeUsageId == null;
    }
}
