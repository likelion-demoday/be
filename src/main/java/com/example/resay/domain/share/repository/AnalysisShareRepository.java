package com.example.resay.domain.share.repository;

import com.example.resay.domain.share.entity.AnalysisShare;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisShareRepository extends JpaRepository<AnalysisShare, Long> {

    Optional<AnalysisShare> findByRecordingId(Long recordingId);

    Optional<AnalysisShare> findByTokenHash(String tokenHash);

    void deleteByRecordingId(Long recordingId);
}
