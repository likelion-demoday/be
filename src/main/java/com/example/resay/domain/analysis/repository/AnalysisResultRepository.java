package com.example.resay.domain.analysis.repository;

import com.example.resay.domain.analysis.entity.AnalysisResult;
import com.example.resay.domain.analysis.entity.AnalysisStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {

    Optional<AnalysisResult> findByAnalysisId(Long analysisId);

    boolean existsByAnalysisId(Long analysisId);

    @Query("""
            select a.recordingId as recordingId,
                   a.updatedAt as analyzedAt,
                   r.parentChildRole as parentChildRole,
                   ar.resultJson as resultJson
            from AnalysisResult ar, ConversationAnalysis a, Recording r
            where ar.analysisId = a.id
              and a.recordingId = r.id
              and r.userId = :userId
              and a.status = :status
            order by a.updatedAt desc, a.id desc
            """)
    List<AnalysisSummarySourceProjection> findRecentSummarySources(
            @Param("userId") Long userId,
            @Param("status") AnalysisStatus status,
            Pageable pageable
    );
}
