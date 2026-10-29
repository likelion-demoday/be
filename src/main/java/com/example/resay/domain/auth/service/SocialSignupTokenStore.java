package com.example.resay.domain.auth.service;

import com.example.resay.domain.user.entity.Provider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 소셜 로그인은 했지만 아직 가입(약관 동의)을 마치지 않은 사용자를 가입 완료 요청까지 기억한다.
 * 동의 전에는 계정도 이메일도 DB에 남기지 않는다.
 * shortcut: 서버 메모리에 둔다 (서버 1대 기준). 온보딩 중에 서버가 재시작되면 다시 소셜 로그인해야 한다. 서버를 늘리면 DB로 옮긴다
 */
@Component
public class SocialSignupTokenStore {

    // 온보딩(닉네임 → 목소리 등록 → 약관 동의)을 마칠 만큼
    static final Duration VALIDITY = Duration.ofMinutes(30);

    private final Map<String, PendingSocialSignup> pendings = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public SocialSignupTokenStore() {
        this(Clock.systemUTC());
    }

    SocialSignupTokenStore(Clock clock) {
        this.clock = clock;
    }

    /**
     * 가입용 임시 토큰을 발급한다. JWT가 아닌 무작위 값이라 Access Token 자리에 넣어도 인증되지 않는다.
     */
    public IssuedSignupToken issue(Provider provider, String providerId, String email) {
        Instant now = clock.instant();
        // 가입하지 않고 떠난 사용자의 이메일이 메모리에 계속 남지 않게 발급할 때 만료분을 정리한다
        pendings.values().removeIf(pending -> pending.isExpired(now));

        String token = RefreshTokenService.generateRawToken();
        pendings.put(token, new PendingSocialSignup(provider, providerId, email, now.plus(VALIDITY)));
        return new IssuedSignupToken(token, VALIDITY.toSeconds());
    }

    /**
     * 토큰을 꺼내면서 지운다. 같은 토큰으로 동시에 요청해도 한 요청만 받는다.
     */
    public Optional<PendingSocialSignup> consume(String token) {
        return Optional.ofNullable(pendings.remove(token))
                .filter(pending -> !pending.isExpired(clock.instant()));
    }

    // email은 소셜 계정이 제공한 값 그대로다 (제공하지 않았으면 null)
    public record PendingSocialSignup(Provider provider, String providerId, String email, Instant expiresAt) {

        boolean isExpired(Instant now) {
            return !now.isBefore(expiresAt);
        }
    }

    public record IssuedSignupToken(String value, long expiresInSeconds) {
    }
}
