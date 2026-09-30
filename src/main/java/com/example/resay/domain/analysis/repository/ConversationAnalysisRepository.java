package com.example.resay.domain.analysis.repository;

import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationAnalysisRepository extends JpaRepository<ConversationAnalysis, Long> {

    Optional<ConversationAnalysis> findByRecordingId(Long recordingId);

    boolean existsByRecordingId(Long recordingId);
}
