package com.example.resay.domain.credit.repository;

import com.example.resay.domain.credit.entity.CreditUsage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

// 조회 전용. 사용 건을 추가하거나 환급 처리하는 일은 CreditJdbcRepository가 한다
public interface CreditUsageRepository extends JpaRepository<CreditUsage, Long> {

    // 유효한(USED) 사용 건. activeKey는 CreditUsage.activeKey(용도, 녹음)로 만든다
    Optional<CreditUsage> findByActiveKey(String activeKey);
}
