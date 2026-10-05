package com.example.resay.domain.analysis.repository;

import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationAnalysisRepository extends JpaRepository<ConversationAnalysis, Long> {

    Optional<ConversationAnalysis> findByRecordingId(Long recordingId);

    boolean existsByRecordingId(Long recordingId);

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
