package com.example.resay.domain.analysis.repository;

import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationAnalysisRepository extends JpaRepository<ConversationAnalysis, Long> {

    Optional<ConversationAnalysis> findByRecordingId(Long recordingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ConversationAnalysis a where a.recordingId = :recordingId")
    Optional<ConversationAnalysis> findByRecordingIdForUpdate(
            @Param("recordingId") Long recordingId
    );

    boolean existsByRecordingId(Long recordingId);

    @Query("""
            select a from ConversationAnalysis a
            where a.status = com.example.resay.domain.analysis.entity.AnalysisStatus.ANALYZING
              and a.updatedAt < :cutoff
            order by a.id
            """)
    List<ConversationAnalysis> findStalledAnalyzing(@Param("cutoff") LocalDateTime cutoff);

    @Query("""
            select r.id from Recording r
            where r.status = com.example.resay.domain.recording.entity.RecordingStatus.ANALYZING
              and r.updatedAt < :cutoff
              and not exists (
                  select a.id from ConversationAnalysis a where a.recordingId = r.id
              )
            order by r.id
            """)
    List<Long> findStalledAnalyzingRecordingIdsWithoutAnalysis(
            @Param("cutoff") LocalDateTime cutoff
    );

    // 조회 뒤 완료 처리와 경합하더라도 아직 멈춰 있는 ANALYZING 행만 실패로 바꾼다.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update ConversationAnalysis a
               set a.status = com.example.resay.domain.analysis.entity.AnalysisStatus.FAILED,
                   a.failureReason = com.example.resay.domain.analysis.entity.AnalysisFailureReason.PROCESSING_ERROR,
                   a.updatedAt = :now
             where a.id = :analysisId
               and a.recordingId = :recordingId
               and a.status = com.example.resay.domain.analysis.entity.AnalysisStatus.ANALYZING
               and a.updatedAt < :cutoff
            """)
    int markStalledFailed(
            @Param("analysisId") Long analysisId,
            @Param("recordingId") Long recordingId,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("now") LocalDateTime now
    );

    // 분석 실패 커밋 뒤 녹음 반영 전에 서버가 종료된 건을 복구한다.
    @Query("""
            select a from ConversationAnalysis a, Recording r
            where a.recordingId = r.id
              and a.status = com.example.resay.domain.analysis.entity.AnalysisStatus.FAILED
              and r.status = com.example.resay.domain.recording.entity.RecordingStatus.ANALYZING
            order by a.id
            """)
    List<ConversationAnalysis> findFailedWithAnalyzingRecording();

    @Query(
            value = """
                    select r.id as recordingId,
                           r.title as title,
                           r.relationshipType as relationshipType,
                           r.durationSeconds as durationSeconds,
                           r.createdAt as createdAt,
                           a.status as analysisStatus,
                           a.failureReason as failureReason
                    from ConversationAnalysis a, Recording r
                    where a.recordingId = r.id
                      and r.userId = :userId
                    order by r.createdAt desc, r.id desc
                    """,
            countQuery = """
                    select count(a)
                    from ConversationAnalysis a, Recording r
                    where a.recordingId = r.id
                      and r.userId = :userId
                    """
    )
    Page<AnalysisListItemProjection> findPageByUserId(
            @Param("userId") Long userId,
            Pageable pageable
    );
}
