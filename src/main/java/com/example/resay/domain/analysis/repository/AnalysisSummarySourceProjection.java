package com.example.resay.domain.analysis.repository;

import com.example.resay.domain.recording.entity.ParentChildRole;
import java.time.LocalDateTime;

public interface AnalysisSummarySourceProjection {

    Long getRecordingId();

    LocalDateTime getAnalyzedAt();

    ParentChildRole getParentChildRole();

    String getResultJson();
}
