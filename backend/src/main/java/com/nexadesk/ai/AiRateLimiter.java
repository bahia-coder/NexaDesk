package com.nexadesk.ai;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Sliding one-minute window of LLM calls per user, in memory. Enough for a single API instance; several
 * instances would share it through Redis or the database. Past the limit, the heuristic answers instead.
 */
@Component
public class AiRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<Long, Deque<Instant>> calls = new ConcurrentHashMap<>();
    private final int perMinute;
    private final Clock clock;

    public AiRateLimiter(AiProperties properties, Clock clock) {
        this.perMinute = properties.requestsPerMinute();
        this.clock = clock;
    }

    public boolean tryAcquire(Long userId) {
        Instant now = clock.instant();
        Deque<Instant> window = calls.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && !window.peekFirst().isAfter(now.minus(WINDOW))) {
                window.pollFirst();
            }
            if (window.size() >= perMinute) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }
}
