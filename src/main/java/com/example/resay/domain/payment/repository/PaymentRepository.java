package com.example.resay.domain.payment.repository;

import com.example.resay.domain.payment.entity.Payment;
import com.example.resay.domain.payment.entity.PaymentStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상태를 바꾸는 메서드는 모두 "지금 상태가 기대한 값일 때만" 바꾸고, 바꾼 행 수를 돌려준다.
 * 같은 주문을 여러 요청이 동시에 처리하려 해도 1을 받은 한 요청만 다음 단계로 간다.
 */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(String orderId);

    Optional<Payment> findByOrderIdAndUserId(String orderId, Long userId);

    Slice<Payment> findByUserIdAndStatusOrderByIdDesc(Long userId, PaymentStatus status, Pageable pageable);

    // 승인을 요청한 뒤 결과가 확정되지 않은 채 남아 있는 주문
    List<Payment> findByStatusAndApprovalRequestedAtBeforeOrderByIdAsc(PaymentStatus status, LocalDateTime cutoff);

    long countByStatusAndApprovalRequestedAtBefore(PaymentStatus status, LocalDateTime cutoff);

    // 결제하지 않은 채 오래된 주문 (만료 대상)
    @Query("""
            select p.id from Payment p
            where p.status = com.example.resay.domain.payment.entity.PaymentStatus.READY
              and p.createdAt < :cutoff
            """)
    List<Long> findExpiredReadyIds(@Param("cutoff") LocalDateTime cutoff);

    /**
     * 승인을 요청하기 직전에 호출한다 (READY → APPROVING).
     *
     * @throws org.springframework.dao.DataIntegrityViolationException 다른 주문이 이미 쓴 tid 또는 인증 토큰일 때
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update Payment p
            set p.status = com.example.resay.domain.payment.entity.PaymentStatus.APPROVING,
                p.tid = :tid, p.authTokenHash = :authTokenHash, p.approvalRequestedAt = :now, p.updatedAt = :now
            where p.id = :id
              and p.status = com.example.resay.domain.payment.entity.PaymentStatus.READY
            """)
    int markApproving(
            @Param("id") Long id,
            @Param("tid") String tid,
            @Param("authTokenHash") String authTokenHash,
            @Param("now") LocalDateTime now
    );

    /** 결제 확정 (APPROVING → PAID). 크레딧 지급과 같은 트랜잭션에서 호출한다. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update Payment p
            set p.status = com.example.resay.domain.payment.entity.PaymentStatus.PAID,
                p.paidAt = :paidAt, p.receiptUrl = :receiptUrl, p.updatedAt = :now
            where p.id = :id
              and p.status = com.example.resay.domain.payment.entity.PaymentStatus.APPROVING
            """)
    int markPaid(
            @Param("id") Long id,
            @Param("paidAt") LocalDateTime paidAt,
            @Param("receiptUrl") String receiptUrl,
            @Param("now") LocalDateTime now
    );

    /** 결제되지 않고 끝남 (READY 또는 APPROVING → FAILED). */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update Payment p
            set p.status = com.example.resay.domain.payment.entity.PaymentStatus.FAILED,
                p.failCode = :failCode, p.failMessage = :failMessage, p.updatedAt = :now
            where p.id = :id and p.status = :expected
            """)
    int markFailed(
            @Param("id") Long id,
            @Param("expected") PaymentStatus expected,
            @Param("failCode") String failCode,
            @Param("failMessage") String failMessage,
            @Param("now") LocalDateTime now
    );

    /**
     * 결제하지 않은 주문을 만료 처리한다 (READY → EXPIRED).
     * 조건(상태 · 날짜)으로 여러 행을 한 번에 바꾸지 않고 ID로 한 행씩 바꾼다. 상태 인덱스를 따라 잠그는 UPDATE는
     * 같은 행을 기본키로 먼저 잠그는 다른 전환(인증 결과 처리)과 잠금 순서가 반대라 MySQL에서 교착될 수 있다.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update Payment p
            set p.status = com.example.resay.domain.payment.entity.PaymentStatus.EXPIRED, p.updatedAt = :now
            where p.id = :id
              and p.status = com.example.resay.domain.payment.entity.PaymentStatus.READY
            """)
    int markExpired(@Param("id") Long id, @Param("now") LocalDateTime now);
}
