package com.example.resay.domain.auth.service;

import com.example.resay.global.exception.RateLimitExceededException;
import com.example.resay.global.security.ratelimit.RateLimitProperties;
import com.example.resay.global.security.ratelimit.SlidingWindowRateLimiter;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 로그인 비밀번호 대입과 대량 가입을 막기 위한 시도 횟수 제한.
 * 로그인은 "실패"만 세므로 정상적으로 로그인하는 사용자는 걸리지 않는다.
 */
@Component
@EnableConfigurationProperties(RateLimitProperties.class)
public class AuthRateLimiter {

    private final boolean enabled;
    private final SlidingWindowRateLimiter loginFailuresByIp;
    private final SlidingWindowRateLimiter loginFailuresByEmail;
    private final SlidingWindowRateLimiter signupAttemptsByIp;

    public AuthRateLimiter(RateLimitProperties properties) {
        Clock clock = Clock.systemUTC();
        this.enabled = properties.enabled();
        this.loginFailuresByIp = new SlidingWindowRateLimiter(
                properties.login().maxFailuresPerIp(), properties.login().window(), clock);
        this.loginFailuresByEmail = new SlidingWindowRateLimiter(
                properties.login().maxFailuresPerEmail(), properties.login().window(), clock);
        this.signupAttemptsByIp = new SlidingWindowRateLimiter(
                properties.signup().maxAttemptsPerIp(), properties.signup().window(), clock);
    }

    /**
     * 비밀번호를 확인하기 전에 호출한다. 한도를 넘었으면 예외를 던진다.
     * 먼저 실패 1회를 잡아 두고 결과에 따라 되돌린다. 확인만 하고 나중에 기록하면
     * 동시에 보낸 요청들이 모두 확인을 통과해 한도를 넘길 수 있다.
     *
     * @param email 소문자로 통일된 이메일. 가입 여부와 관계없이 센다 (가입된 이메일인지 드러나지 않게)
     */
    public LoginAttempt beginLogin(String clientIp, String email) {
        if (!enabled) {
            return LoginAttempt.NOT_LIMITED;
        }

        Duration ipRetryAfter = loginFailuresByIp.tryAcquire(clientIp);
        if (!ipRetryAfter.isZero()) {
            throw new RateLimitExceededException(ipRetryAfter);
        }
        Duration emailRetryAfter = loginFailuresByEmail.tryAcquire(email);
        if (!emailRetryAfter.isZero()) {
            // 이미 막힌 이메일로 온 요청은 비밀번호를 확인하지 않으므로 IP의 실패로 세지 않는다
            loginFailuresByIp.release(clientIp);
            throw new RateLimitExceededException(emailRetryAfter);
        }
        return new LoginAttempt(this, clientIp, email);
    }

    /** 가입은 성공 여부와 관계없이 시도 자체를 센다. */
    public void checkSignup(String clientIp) {
        if (!enabled) {
            return;
        }
        Duration retryAfter = signupAttemptsByIp.tryAcquire(clientIp);
        if (!retryAfter.isZero()) {
            throw new RateLimitExceededException(retryAfter);
        }
    }

    public static final class LoginAttempt {

        private static final LoginAttempt NOT_LIMITED = new LoginAttempt(null, null, null);

        private final AuthRateLimiter limiter;
        private final String clientIp;
        private final String email;

        private LoginAttempt(AuthRateLimiter limiter, String clientIp, String email) {
            this.limiter = limiter;
            this.clientIp = clientIp;
            this.email = email;
        }

        /** 로그인 성공. 이 이메일의 실패 기록을 지우고, IP에 잡아 둔 1회도 되돌린다. */
        public void succeeded() {
            if (limiter == null) {
                return;
            }
            limiter.loginFailuresByIp.release(clientIp);
            limiter.loginFailuresByEmail.reset(email);
        }

        /** 비밀번호와 무관한 이유(서버 오류 등)로 끝난 경우. 실패로 세지 않는다. */
        public void cancelled() {
            if (limiter == null) {
                return;
            }
            limiter.loginFailuresByIp.release(clientIp);
            limiter.loginFailuresByEmail.release(email);
        }
    }
}
