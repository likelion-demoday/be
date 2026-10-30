package com.example.resay.global.security.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 키(IP, 이메일 등)별로 "최근 window 동안 limit 회까지"를 허용한다.
 * 기록은 서버 메모리에만 두므로 서버가 1대일 때만 유효하고, 재시작하면 초기화된다.
 */
public class SlidingWindowRateLimiter {

    private static final long CLEANUP_INTERVAL_MILLIS = Duration.ofMinutes(1).toMillis();

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    // 키별 기록 시각(오래된 것부터). 한 키의 목록은 compute 안에서만 읽고 고친다
    private final ConcurrentHashMap<String, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupMillis;

    public SlidingWindowRateLimiter(int limit, Duration window, Clock clock) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit은 1 이상이어야 합니다.");
        }
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window는 양수여야 합니다.");
        }
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
        this.lastCleanupMillis = new AtomicLong(clock.millis());
    }

    /**
     * 한도에 여유가 있으면 1회를 기록하고 0을 반환한다.
     * 한도를 넘었으면 기록하지 않고, 다시 시도할 수 있을 때까지 남은 시간을 반환한다.
     * 확인과 기록을 한 번에 처리하므로 동시에 요청이 몰려도 한도를 넘지 않는다.
     */
    public Duration tryAcquire(String key) {
        long now = clock.millis();
        cleanupIfDue(now);

        long[] retryAfterMillis = {0};
        attempts.compute(key, (ignored, times) -> {
            Deque<Long> current = times == null ? new ArrayDeque<>() : times;
            evictExpired(current, now);
            if (current.size() < limit) {
                current.addLast(now);
            } else {
                // 가장 오래된 기록이 window를 벗어나는 시점에 자리가 난다
                retryAfterMillis[0] = current.peekFirst() + windowMillis - now;
            }
            return current;
        });
        return Duration.ofMillis(retryAfterMillis[0]);
    }

    /** 가장 최근 기록 1건을 취소한다. (세지 않기로 한 시도를 되돌릴 때) */
    public void release(String key) {
        attempts.computeIfPresent(key, (ignored, times) -> {
            times.pollLast();
            return times.isEmpty() ? null : times;
        });
    }

    public void reset(String key) {
        attempts.remove(key);
    }

    // 다시 오지 않는 키의 기록이 메모리에 계속 남지 않도록 주기적으로 지운다
    private void cleanupIfDue(long now) {
        long last = lastCleanupMillis.get();
        if (now - last < CLEANUP_INTERVAL_MILLIS || !lastCleanupMillis.compareAndSet(last, now)) {
            return;
        }
        for (String key : attempts.keySet()) {
            attempts.computeIfPresent(key, (ignored, times) -> {
                evictExpired(times, now);
                return times.isEmpty() ? null : times;
            });
        }
    }

    private void evictExpired(Deque<Long> times, long now) {
        while (!times.isEmpty() && now - times.peekFirst() >= windowMillis) {
            times.pollFirst();
        }
    }

    int trackedKeyCount() {
        return attempts.size();
    }
}
