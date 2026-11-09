package com.example.resay.domain.share.entity;

import com.example.resay.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Getter
@Entity
@Table(
        name = "analysis_shares",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_analysis_shares_recording_id",
                        columnNames = "recording_id"
                ),
                @UniqueConstraint(
                        name = "uk_analysis_shares_analysis_id",
                        columnNames = "analysis_id"
                ),
                @UniqueConstraint(
                        name = "uk_analysis_shares_token_hash",
                        columnNames = "token_hash"
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalysisShare extends BaseEntity {

    private static final int TOKEN_HASH_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recording_id", nullable = false, updatable = false)
    private Long recordingId;

    @Column(name = "analysis_id", nullable = false)
    private Long analysisId;

    @Column(name = "token_hash", nullable = false, length = TOKEN_HASH_LENGTH)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    private AnalysisShare(
            Long recordingId,
            Long analysisId,
            String tokenHash,
            LocalDateTime expiresAt
    ) {
        validateRecordingId(recordingId);
        validateAnalysisId(analysisId);
        validateTokenHash(tokenHash);
        validateExpiresAt(expiresAt);
        this.recordingId = recordingId;
        this.analysisId = analysisId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static AnalysisShare create(
            Long recordingId,
            Long analysisId,
            String tokenHash,
            LocalDateTime expiresAt
    ) {
        return new AnalysisShare(recordingId, analysisId, tokenHash, expiresAt);
    }

    public void rotate(Long analysisId, String tokenHash, LocalDateTime expiresAt) {
        validateAnalysisId(analysisId);
        validateTokenHash(tokenHash);
        validateExpiresAt(expiresAt);
        this.analysisId = analysisId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    private static void validateRecordingId(Long recordingId) {
        if (recordingId == null || recordingId <= 0) {
            throw new IllegalArgumentException("recordingId는 양수여야 합니다.");
        }
    }

    private static void validateTokenHash(String tokenHash) {
        if (!StringUtils.hasText(tokenHash) || tokenHash.length() != TOKEN_HASH_LENGTH) {
            throw new IllegalArgumentException("공유 토큰 해시가 올바르지 않습니다.");
        }
    }

    private static void validateAnalysisId(Long analysisId) {
        if (analysisId == null || analysisId <= 0) {
            throw new IllegalArgumentException("analysisId는 양수여야 합니다.");
        }
    }

    private static void validateExpiresAt(LocalDateTime expiresAt) {
        if (expiresAt == null) {
            throw new IllegalArgumentException("공유 링크 만료 시각은 비어 있을 수 없습니다.");
        }
    }
}
