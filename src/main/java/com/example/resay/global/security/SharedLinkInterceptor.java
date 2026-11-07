package com.example.resay.global.security;

import com.example.resay.global.exception.RateLimitExceededException;
import com.example.resay.global.security.ratelimit.RateLimitProperties;
import com.example.resay.global.security.ratelimit.SlidingWindowRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 로그인 없이 열린 공유 링크 API(/api/v1/shared/**)의 공통 보호.
 * 공유된 보고서가 캐시 · 검색엔진 · 다른 사이트로 새지 않게 응답 헤더를 붙이고, IP별 요청 횟수를 제한한다.
 * 공유 토큰 검증은 여기서 하지 않는다. 이 경로 아래의 API가 각자 반드시 검증해야 한다.
 */
@Component
public class SharedLinkInterceptor implements HandlerInterceptor {

    public static final String PATH_PATTERN = "/api/v1/shared/**";

    private final boolean enabled;
    private final SlidingWindowRateLimiter requestsByIp;

    public SharedLinkInterceptor(RateLimitProperties properties) {
        this.enabled = properties.enabled();
        this.requestsByIp = new SlidingWindowRateLimiter(
                properties.shared().maxRequestsPerIp(), properties.shared().window(), Clock.systemUTC());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Robots-Tag", "noindex, nofollow, noarchive");

        if (enabled) {
            Duration retryAfter = requestsByIp.tryAcquire(request.getRemoteAddr());
            if (!retryAfter.isZero()) {
                throw new RateLimitExceededException(retryAfter);
            }
        }
        return true;
    }
}
