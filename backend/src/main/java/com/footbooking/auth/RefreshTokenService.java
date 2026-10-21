package com.footbooking.auth;

import com.footbooking.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {

    /** Jeton en clair (renvoye une seule fois au client) et proprietaire. */
    public record Issued(UUID userId, String rawToken) {}

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository tokens;
    private final Clock clock;
    private final long validDays;

    public RefreshTokenService(RefreshTokenRepository tokens, Clock clock,
                               @Value("${app.jwt.refresh-days:30}") long validDays) {
        this.tokens = tokens;
        this.clock = clock;
        this.validDays = validDays;
    }

    /** Emet un jeton. Seul son hash est stocke. */
    @Transactional
    public String issue(UUID userId) {
        String raw = newRawToken();
        RefreshToken t = new RefreshToken();
        t.setUserId(userId);
        t.setTokenHash(hash(raw));
        t.setExpiresAt(clock.instant().plus(validDays, ChronoUnit.DAYS));
        tokens.save(t);
        return raw;
    }

    /**
     * Rotation : l'ancien jeton est revoque, un nouveau est emis.
     * Un jeton deja revoque presente a nouveau signale un vol probable : tous les jetons de l'utilisateur sont revoques.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public Issued rotate(String rawToken) {
        Instant now = clock.instant();
        RefreshToken t = tokens.findForUpdateByTokenHash(hash(rawToken == null ? "" : rawToken)).orElse(null);
        if (t == null) {
            throw invalid();
        }
        if (t.getRevokedAt() != null) {
            // noRollbackFor : la revocation globale doit survivre a l'exception 401
            tokens.revokeAllForUser(t.getUserId(), now);
            throw invalid();
        }
        if (!t.getExpiresAt().isAfter(now)) {
            throw invalid();
        }
        t.setRevokedAt(now);
        return new Issued(t.getUserId(), issue(t.getUserId()));
    }

    /** Deconnexion : sans erreur si le jeton est inconnu ou deja revoque. */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        tokens.findForUpdateByTokenHash(hash(rawToken)).ifPresent(t -> {
            if (t.getRevokedAt() == null) {
                t.setRevokedAt(clock.instant());
            }
        });
    }

    // ---------- Fonctions pures ----------

    static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Jeton de rafraichissement invalide");
    }
}