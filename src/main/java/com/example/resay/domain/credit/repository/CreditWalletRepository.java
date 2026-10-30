package com.example.resay.domain.credit.repository;

import com.example.resay.domain.credit.entity.CreditWallet;
import org.springframework.data.jpa.repository.JpaRepository;

// 조회 전용. 잔액을 바꾸는 일은 CreditJdbcRepository가 한다
public interface CreditWalletRepository extends JpaRepository<CreditWallet, Long> {
}
