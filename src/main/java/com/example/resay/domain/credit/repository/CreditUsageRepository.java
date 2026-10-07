package com.example.resay.domain.credit.repository;

import com.example.resay.domain.credit.entity.CreditUsage;
import com.example.resay.domain.credit.entity.UsagePurpose;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 조회 전용. 사용 건을 추가하거나 환급 처리하는 일은 CreditJdbcRepository가 한다
public interface CreditUsageRepository extends JpaRepository<CreditUsage, Long> {

    // 유효한(USED) 사용 건. activeKey는 CreditUsage.activeKey(용도, 녹음)로 만든다
    Optional<CreditUsage> findByActiveKey(String activeKey);

    // 차감된 채 남아 있는데 녹음은 이미 실패했거나 없어진 건 (환급이 누락된 건)
    @Query("""
            select u.recordingId from CreditUsage u
            where u.purpose = :purpose
              and u.status = com.example.resay.domain.credit.entity.UsageStatus.USED
              and u.createdAt < :cutoff
              and not exists (
                    select r.id from Recording r
                    where r.id = u.recordingId
                      and r.status <> com.example.resay.domain.recording.entity.RecordingStatus.FAILED)
            """)
    List<Long> findUnrefundedFailedAnalysisRecordingIds(
            @Param("purpose") UsagePurpose purpose,
            @Param("cutoff") LocalDateTime cutoff
    );
}
