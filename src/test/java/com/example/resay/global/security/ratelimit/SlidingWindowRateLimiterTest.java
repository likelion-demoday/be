package com.example.resay.global.security.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlidingWindowRateLimiterTest {

    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final MutableClock clock = new MutableClock();
    private final SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(3, WINDOW, clock);

    @Test
    void allowsUpToLimitThenBlocks() {
        assertThat(limiter.tryAcquire("a")).isZero();
        assertThat(limiter.tryAcquire("a")).isZero();
        assertThat(limiter.tryAcquire("a")).isZero();

        assertThat(limiter.tryAcquire("a")).isEqualTo(WINDOW);
    }

    @Test
    void countsEachKeySeparately() {
        acquire("a", 3);

        assertThat(limiter.tryAcquire("a")).isPositive();
        assertThat(limiter.tryAcquire("b")).isZero();
    }

    @Test
    void retryAfterIsTimeUntilOldestAttemptExpires() {
        limiter.tryAcquire("a");
        clock.advance(Duration.ofMinutes(4));
        acquire("a", 2);
        clock.advance(Duration.ofMinutes(1));

        assertThat(limiter.tryAcquire("a")).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void allowsAgainOnlyAsOldAttemptsLeaveWindow() {
        limiter.tryAcquire("a");
        clock.advance(Duration.ofMinutes(4));
        acquire("a", 2);

        // 첫 기록이 window를 벗어나면 1회만 다시 허용된다
        clock.advance(Duration.ofMinutes(6));
        assertThat(limiter.tryAcquire("a")).isZero();
        assertThat(limiter.tryAcquire("a")).isEqualTo(Duration.ofMinutes(4));
    }

    @Test
    void blockedAttemptsAreNotRecorded() {
        acquire("a", 3);
        clock.advance(Duration.ofMinutes(9));
        // 막힌 상태에서 계속 시도해도 차단 시간이 늘어나지 않는다
        for (int i = 0; i < 10; i++) {
            assertThat(limiter.tryAcquire("a")).isEqualTo(Duration.ofMinutes(1));
        }

        clock.advance(Duration.ofMinutes(1));
        assertThat(limiter.tryAcquire("a")).isZero();
    }

    @Test
    void releaseCancelsMostRecentAttempt() {
        acquire("a", 3);

        limiter.release("a");

        assertThat(limiter.tryAcquire("a")).isZero();
        assertThat(limiter.tryAcquire("a")).isPositive();
    }

    @Test
    void releaseOnUnknownKeyDoesNothing() {
        limiter.release("unknown");

        assertThat(limiter.trackedKeyCount()).isZero();
    }

    @Test
    void resetClearsAllAttemptsOfKey() {
        acquire("a", 3);

        limiter.reset("a");

        acquire("a", 3);
        assertThat(limiter.tryAcquire("a")).isPositive();
    }

    @Test
    void removesExpiredKeysFromMemory() {
        for (int i = 0; i < 100; i++) {
            limiter.tryAcquire("key-" + i);
        }
        assertThat(limiter.trackedKeyCount()).isEqualTo(100);

        clock.advance(WINDOW.plusMinutes(1));
        limiter.tryAcquire("another");

        assertThat(limiter.trackedKeyCount()).isEqualTo(1);
    }

    @Test
    void neverExceedsLimitUnderConcurrentRequests() throws Exception {
        SlidingWindowRateLimiter concurrentLimiter = new SlidingWindowRateLimiter(20, WINDOW, Clock.systemUTC());
        int threads = 16;
        int attemptsPerThread = 200;
        AtomicInteger acquired = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            for (int t = 0; t < threads; t++) {
                executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < attemptsPerThread; i++) {
                        if (concurrentLimiter.tryAcquire("a").isZero()) {
                            acquired.incrementAndGet();
                        }
                    }
                    return null;
                });
            }
            start.countDown();
        } finally {
            executor.shutdown();
        }
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(acquired.get()).isEqualTo(20);
    }

    @Test
    void rejectsInvalidSettings() {
        assertThatThrownBy(() -> new SlidingWindowRateLimiter(0, WINDOW, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SlidingWindowRateLimiter(3, Duration.ZERO, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void acquire(String key, int times) {
        for (int i = 0; i < times; i++) {
            assertThat(limiter.tryAcquire(key)).isZero();
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-10-02T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
