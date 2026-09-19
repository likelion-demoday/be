package com.example.resay.domain.auth.service;

import com.example.resay.domain.auth.code.AuthErrorCode;
import com.example.resay.domain.auth.entity.RefreshToken;
import com.example.resay.domain.auth.repository.RefreshTokenRepository;
import com.example.resay.global.exception.GeneralException;
import com.example.resay.global.security.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;

    @Transactional
    public IssuedRefreshToken issue(Long userId) {
        Instant now = Instant.now();
        // 만료된 토큰이 계속 쌓이지 않도록 발급할 때 그 사용자의 만료분을 정리한다
        refreshTokenRepository.deleteExpiredByUserId(userId, now);

        String rawToken = generateRawToken();
        Instant expiresAt = now.plus(jwtProperties.refreshTokenExpiration());
        refreshTokenRepository.save(RefreshToken.create(userId, hash(rawToken), expiresAt));
        return new IssuedRefreshToken(rawToken, jwtProperties.refreshTokenExpiration().toSeconds());
    }

    /**
     * 토큰을 소비하고 주인의 사용자 ID를 돌려준다. 사용한 토큰은 폐기되어 다시 쓸 수 없다.
     * 만료된 토큰은 예외로 롤백돼 여기서 지워지지 않아도 다음 발급 때 정리된다.
     */
    @Transactional
    public Long consume(String rawToken) {
        String tokenHash = hash(rawToken);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new GeneralException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        if (!deleteForConsume(tokenHash) || refreshToken.isExpired(Instant.now())) {
            throw new GeneralException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        return refreshToken.getUserId();
    }

    // 같은 토큰으로 동시에 재발급을 요청하면 한 요청만 삭제에 성공한다.
    // 나머지는 삭제 건수 0이거나, MySQL이 같은 행을 지우려는 트랜잭션끼리의 데드락으로 끊는다.
    // 어느 쪽이든 "이미 사용된 토큰"이므로 500이 아니라 401로 응답한다.
    private boolean deleteForConsume(String tokenHash) {
        try {
            return refreshTokenRepository.deleteByTokenHash(tokenHash) == 1;
        } catch (PessimisticLockingFailureException exception) {
            return false;
        }
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.deleteByTokenHash(hash(rawToken));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // 무작위 256비트 토큰이라 BCrypt 같은 느린 해시 없이 SHA-256으로 충분하다
    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    public record IssuedRefreshToken(String value, long expiresInSeconds) {
    }
}
