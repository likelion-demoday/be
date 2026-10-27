package com.example.resay.domain.analysis.repository;

import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
