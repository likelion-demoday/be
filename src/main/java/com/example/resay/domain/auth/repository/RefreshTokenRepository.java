package com.example.resay.domain.auth.repository;

import com.example.resay.domain.auth.entity.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // 삭제된 행 수로 "이 요청이 토큰을 실제로 소비했는지" 판단한다 (동시 재발급 요청 중 하나만 성공)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RefreshToken r where r.tokenHash = :tokenHash")
    int deleteByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from RefreshToken r where r.userId = :userId and r.expiresAt <= :now")
    int deleteExpiredByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
