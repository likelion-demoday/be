package com.example.resay.domain.auth.entity;

import com.example.resay.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "refresh_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_tokens_token_hash", columnNames = "token_hash"),
        indexes = @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 도메인 간 엔티티 직접 참조 대신 ID로 연결한다
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 토큰 원문은 저장하지 않고 SHA-256 해시(hex 64자)만 저장한다
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private RefreshToken(Long userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken create(Long userId, String tokenHash, Instant expiresAt) {
        if (userId == null || tokenHash == null || expiresAt == null) {
            throw new IllegalArgumentException("리프레시 토큰의 필수값이 비어 있습니다.");
        }
        return new RefreshToken(userId, tokenHash, expiresAt);
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
