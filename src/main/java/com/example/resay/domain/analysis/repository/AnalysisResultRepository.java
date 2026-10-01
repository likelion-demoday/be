package com.example.resay.domain.analysis.repository;

import com.example.resay.domain.analysis.entity.AnalysisResult;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisResultRepository extends JpaRepository<AnalysisResult, Long> {

    Optional<AnalysisResult> findByAnalysisId(Long analysisId);

    boolean existsByAnalysisId(Long analysisId);
}
