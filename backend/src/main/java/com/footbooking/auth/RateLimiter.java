package com.footbooking.auth;

import java.time.Duration;

/** Limiteur de debit. Une implementation Redis pourra remplacer la version en memoire sans toucher au filtre. */
public interface RateLimiter {

    /** Resultat : allowed vrai si la requete passe ; retryAfterSeconds utile seulement si refusee. */
    record Decision(boolean allowed, long retryAfterSeconds) {}

    Decision tryAcquire(String key, int limit, Duration window);
}