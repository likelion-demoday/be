package com.example.resay.domain.analysis.entity;

import com.example.resay.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "conversation_analyses",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_conversation_analyses_recording_id",
                columnNames = "recording_id"
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationAnalysis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recording_id", nullable = false, updatable = false)
    private Long recordingId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private AnalysisStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_reason", columnDefinition = "varchar(50)")
    private AnalysisFailureReason failureReason;

    private ConversationAnalysis(Long recordingId) {
        this.recordingId = recordingId;
        this.status = AnalysisStatus.ANALYZING;
    }

    public static ConversationAnalysis start(Long recordingId) {
        if (recordingId == null || recordingId <= 0) {
            throw new IllegalArgumentException("recordingId는 양수여야 합니다.");
        }
        return new ConversationAnalysis(recordingId);
    }

    public void complete() {
        requireAnalyzing();
        this.status = AnalysisStatus.COMPLETED;
    }

    public void fail(AnalysisFailureReason failureReason) {
        requireAnalyzing();
        if (failureReason == null) {
            throw new IllegalArgumentException("failureReason은 비어 있을 수 없습니다.");
        }
        this.status = AnalysisStatus.FAILED;
        this.failureReason = failureReason;
    }

    private void requireAnalyzing() {
        if (status != AnalysisStatus.ANALYZING) {
            throw new IllegalStateException("진행 중인 분석만 상태를 변경할 수 있습니다.");
        }
    }
}
