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

    /**
     * 차감된 채 남아 있는데 캐릭터 이미지가 다 만들어지지 않았고, cutoff 이후로 진행도 없는 캐릭터 사용 건.
     * 서버 재시작으로 생성이 멈춘 건(이미지가 PENDING · GENERATING에 남음)과 실패했는데 환급 호출이 실패한 건이다.
     *
     * 진행은 실패하지 않은 이미지의 상태가 바뀐 시각(updatedAt)으로 본다. 생성 중이면 이미지마다 시작 · 완료 때 갱신되고,
     * 실패한 이미지를 다시 요청하면 PENDING으로 돌아가면서 갱신된다.
     * 이미지가 하나도 없으면(분석 삭제 등) 결과를 받았는지 알 수 없으므로 대상이 아니다.
     */
    @Query("""
            select u from CreditUsage u
            where u.purpose = com.example.resay.domain.credit.entity.UsagePurpose.CHARACTER
              and u.status = com.example.resay.domain.credit.entity.UsageStatus.USED
              and u.createdAt < :cutoff
              and exists (
                    select unfinished.id from CharacterImage unfinished, ConversationAnalysis analysis
                    where analysis.recordingId = u.recordingId
                      and unfinished.analysisId = analysis.id
                      and unfinished.status <> com.example.resay.domain.character.entity.CharacterImageStatus.COMPLETED)
              and not exists (
                    select recent.id from CharacterImage recent, ConversationAnalysis recentAnalysis
                    where recentAnalysis.recordingId = u.recordingId
                      and recent.analysisId = recentAnalysis.id
                      and recent.status <> com.example.resay.domain.character.entity.CharacterImageStatus.FAILED
                      and recent.updatedAt >= :cutoff)
            order by u.id
            """)
    List<CreditUsage> findStalledCharacterUsages(@Param("cutoff") LocalDateTime cutoff);
}
