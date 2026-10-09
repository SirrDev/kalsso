package com.footbooking.auth;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Fenetre glissante en memoire, par cle. Valable pour une seule instance. */
@Component
public class InMemoryRateLimiter implements RateLimiter {

    static final int MAX_KEYS = 10_000;

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimiter(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Decision tryAcquire(String key, int limit, Duration window) {
        long now = clock.millis();
        long windowMs = window.toMillis();
        if (hits.size() >= MAX_KEYS && !hits.containsKey(key)) {
            purge(now, windowMs);
            if (hits.size() >= MAX_KEYS) {
                // Memoire bornee : en cas de saturation, on refuse la nouvelle cle plutot que de grossir
                return new Decision(false, window.toSeconds());
            }
        }
        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() <= now - windowMs) {
                deque.pollFirst();
            }
            if (deque.size() >= limit) {
                long retryMs = deque.peekFirst() + windowMs - now;
                return new Decision(false, Math.max(1, (retryMs + 999) / 1000));
            }
            deque.addLast(now);
            return new Decision(true, 0);
        }
    }

    private void purge(long now, long windowMs) {
        for (Iterator<Map.Entry<String, Deque<Long>>> it = hits.entrySet().iterator(); it.hasNext(); ) {
            Deque<Long> d = it.next().getValue();
            synchronized (d) {
                if (d.isEmpty() || d.peekLast() <= now - windowMs) {
                    it.remove();
                }
            }
        }
    }
}