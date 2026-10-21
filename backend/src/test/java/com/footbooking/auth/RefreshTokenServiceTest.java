package com.footbooking.auth;

import com.footbooking.common.ApiException;
import com.footbooking.auth.RefreshTokenService.Issued;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-20T10:00:00Z");

    private final RefreshTokenRepository repo = Mockito.mock(RefreshTokenRepository.class);
    private final RefreshTokenService service =
            new RefreshTokenService(repo, Clock.fixed(NOW, ZoneOffset.UTC), 30);
    private final UUID userId = UUID.randomUUID();

    private RefreshToken stored(String raw, Instant expiresAt, Instant revokedAt) {
        RefreshToken t = new RefreshToken();
        t.setUserId(userId);
        t.setTokenHash(RefreshTokenService.hash(raw));
        t.setExpiresAt(expiresAt);
        t.setRevokedAt(revokedAt);
        when(repo.findForUpdateByTokenHash(RefreshTokenService.hash(raw))).thenReturn(Optional.of(t));
        return t;
    }

    @Test
    void issue_storesOnlyTheHash_andExpiresIn30Days() {
        String raw = service.issue(userId);

        ArgumentCaptor<RefreshToken> cap = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repo).save(cap.capture());
        assertNotEquals(raw, cap.getValue().getTokenHash());
        assertEquals(RefreshTokenService.hash(raw), cap.getValue().getTokenHash());
        assertEquals(64, cap.getValue().getTokenHash().length());
        assertEquals(NOW.plusSeconds(30L * 24 * 3600), cap.getValue().getExpiresAt());
        assertEquals(userId, cap.getValue().getUserId());
    }

    @Test
    void rotate_valid_revokesTheOldAndIssuesANewOne() {
        RefreshToken old = stored("ancien", NOW.plusSeconds(3600), null);

        Issued res = service.rotate("ancien");

        assertEquals(userId, res.userId());
        assertNotEquals("ancien", res.rawToken());
        assertEquals(NOW, old.getRevokedAt());
        verify(repo).save(any(RefreshToken.class));
    }

    @Test
    void rotate_revokedToken_revokesEverythingAndFails() {
        stored("vole", NOW.plusSeconds(3600), NOW.minusSeconds(60));

        ApiException ex = assertThrows(ApiException.class, () -> service.rotate("vole"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        verify(repo).revokeAllForUser(userId, NOW);
        verify(repo, never()).save(any());
    }

    @Test
    void rotate_expiredToken_fails() {
        stored("vieux", NOW.minusSeconds(1), null);

        ApiException ex = assertThrows(ApiException.class, () -> service.rotate("vieux"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        verify(repo, never()).save(any());
    }

    @Test
    void rotate_unknownToken_fails() {
        when(repo.findForUpdateByTokenHash(anyString())).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.rotate("inconnu"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        verify(repo, never()).revokeAllForUser(any(), any());
    }

    @Test
    void revoke_marksTheTokenRevoked() {
        RefreshToken t = stored("actif", NOW.plusSeconds(3600), null);

        service.revoke("actif");

        assertEquals(NOW, t.getRevokedAt());
    }

    @Test
    void revoke_unknownOrBlank_isSilent() {
        when(repo.findForUpdateByTokenHash(anyString())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.revoke("inconnu"));
        assertDoesNotThrow(() -> service.revoke(null));
        assertDoesNotThrow(() -> service.revoke(" "));
    }
}