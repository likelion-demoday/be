package com.example.resay.domain.analysis.repository;

import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import com.example.resay.domain.analysis.entity.AnalysisStatus;
import com.example.resay.domain.recording.entity.RelationshipType;
import java.time.LocalDateTime;

public interface AnalysisListItemProjection {

    Long getRecordingId();

    String getTitle();

    RelationshipType getRelationshipType();

    Integer getDurationSeconds();

    LocalDateTime getCreatedAt();

    AnalysisStatus getAnalysisStatus();

    AnalysisFailureReason getFailureReason();
}
