package com.example.resay.domain.credit.repository;

import com.example.resay.domain.credit.entity.CreditLedgerEntry;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CreditLedgerRepository extends JpaRepository<CreditLedgerEntry, Long> {

    Slice<CreditLedgerEntry> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    List<CreditLedgerEntry> findByUserIdOrderByIdAsc(Long userId);

    // 잔액 검증용: 장부 합계는 항상 지갑 잔액과 같아야 한다
    @Query("select coalesce(sum(e.amount), 0) from CreditLedgerEntry e where e.userId = :userId")
    long sumAmountByUserId(@Param("userId") Long userId);
}
