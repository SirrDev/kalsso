package com.footbooking.auth;

import com.footbooking.auth.RateLimiter.Decision;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryRateLimiterTest {

    private static final Duration MINUTE = Duration.ofMinutes(1);

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-09T10:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };
    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter(clock);

    @Test
    void allowsUpToTheLimitThenRefuses() {
        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire("ip1", 5, MINUTE).allowed());
        }
        Decision d = limiter.tryAcquire("ip1", 5, MINUTE);
        assertFalse(d.allowed());
        assertEquals(60, d.retryAfterSeconds());
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("ip1", 5, MINUTE);
        }
        assertTrue(limiter.tryAcquire("ip2", 5, MINUTE).allowed());
    }

    @Test
    void windowSlides_andRetryAfterShrinks() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("ip1", 5, MINUTE);
        }
        now.set(now.get().plusSeconds(45));
        assertEquals(15, limiter.tryAcquire("ip1", 5, MINUTE).retryAfterSeconds());

        now.set(now.get().plusSeconds(16));
        assertTrue(limiter.tryAcquire("ip1", 5, MINUTE).allowed());
    }

    @Test
    void refusedRequestsDoNotExtendTheBlock() {
        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire("ip1", 5, MINUTE);
        }
        for (int i = 0; i < 20; i++) {
            limiter.tryAcquire("ip1", 5, MINUTE);
        }
        now.set(now.get().plusSeconds(61));
        assertTrue(limiter.tryAcquire("ip1", 5, MINUTE).allowed());
    }
}