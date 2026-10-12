package com.example.resay.domain.payment.repository;

import com.example.resay.domain.payment.entity.PaymentCancel;
import com.example.resay.domain.payment.entity.PaymentCancelStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상태를 바꾸는 메서드는 "아직 결과가 정해지지 않았을 때만" 바꾸고, 바꾼 행 수를 돌려준다.
 * 취소 응답 · 정리 작업 · 웹훅이 같은 취소를 동시에 끝내려 해도 1을 받은 한 곳만 뒤처리(주문 상태 변경, 크레딧 되돌리기)를 한다.
 */
public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {

    List<PaymentCancel> findByPaymentIdOrderByIdAsc(Long paymentId);

    // 한 주문에 진행 중인 취소는 하나뿐이다 (주문이 CANCELLING인 동안에는 새 취소를 시작할 수 없다)
    Optional<PaymentCancel> findFirstByPaymentIdAndStatusOrderByIdDesc(Long paymentId, PaymentCancelStatus status);

    // 취소를 요청한 뒤 결과가 확정되지 않은 채 남아 있는 기록
    List<PaymentCancel> findByStatusAndCreatedAtBeforeOrderByIdAsc(PaymentCancelStatus status, LocalDateTime cutoff);

    long countByStatusAndCreatedAtBefore(PaymentCancelStatus status, LocalDateTime cutoff);

    /** 취소 완료 (REQUESTED → DONE). 주문을 CANCELLED로 바꾸는 것과 같은 트랜잭션에서 호출한다. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update PaymentCancel c
            set c.status = com.example.resay.domain.payment.entity.PaymentCancelStatus.DONE,
                c.cancelledAt = :cancelledAt, c.updatedAt = :now
            where c.id = :id
              and c.status = com.example.resay.domain.payment.entity.PaymentCancelStatus.REQUESTED
            """)
    int markDone(
            @Param("id") Long id,
            @Param("cancelledAt") LocalDateTime cancelledAt,
            @Param("now") LocalDateTime now
    );

    /** 취소되지 않음 (REQUESTED → FAILED). 회수한 크레딧을 되돌리는 것과 같은 트랜잭션에서 호출한다. */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
            update PaymentCancel c
            set c.status = com.example.resay.domain.payment.entity.PaymentCancelStatus.FAILED,
                c.failCode = :failCode, c.failMessage = :failMessage, c.updatedAt = :now
            where c.id = :id
              and c.status = com.example.resay.domain.payment.entity.PaymentCancelStatus.REQUESTED
            """)
    int markFailed(
            @Param("id") Long id,
            @Param("failCode") String failCode,
            @Param("failMessage") String failMessage,
            @Param("now") LocalDateTime now
    );
}
