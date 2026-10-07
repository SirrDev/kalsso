package com.footbooking.notification;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    private final NotificationRepository repo = mock(NotificationRepository.class);
    private final NotificationService service =
            new NotificationService(repo, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void enqueue_cree_une_notification_pending() {
        UUID user = UUID.randomUUID();
        when(repo.existsByDedupeKey("booking-1-confirmed")).thenReturn(false);

        boolean created = service.enqueue(user, "booking_confirmed",
                Map.of("terrain", "Terrain 1"), "booking-1-confirmed");

        assertTrue(created);
        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(repo).save(cap.capture());
        Notification n = cap.getValue();
        assertEquals(user, n.getUserId());
        assertEquals(NotificationChannel.WHATSAPP, n.getChannel());
        assertEquals(NotificationStatus.PENDING, n.getStatus());
        assertEquals("booking_confirmed", n.getTemplate());
        assertEquals("booking-1-confirmed", n.getDedupeKey());
        assertEquals(NOW, n.getSendAt());
        assertEquals("{\"terrain\":\"Terrain 1\"}", n.getPayload());
    }

    @Test
    void enqueue_ignore_un_doublon() {
        when(repo.existsByDedupeKey("k")).thenReturn(true);

        boolean created = service.enqueue(UUID.randomUUID(), "booking_confirmed", Map.of(), "k");

        assertFalse(created);
        verify(repo, never()).save(any());
    }

    @Test
    void enqueue_refuse_un_userId_nul() {
        assertThrows(IllegalArgumentException.class,
                () -> service.enqueue(null, "booking_confirmed", Map.of(), "k"));
        verify(repo, never()).save(any());
    }

    @Test
    void enqueue_refuse_une_cle_trop_longue() {
        String longKey = "x".repeat(121);
        assertThrows(IllegalArgumentException.class,
                () -> service.enqueue(UUID.randomUUID(), "booking_confirmed", Map.of(), longKey));
    }

    @Test
    void json_echappe_guillemets_et_retours_a_la_ligne() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("terrain", "Terrain 1");
        params.put("note", "a\"b\nc");

        assertEquals("{\"terrain\":\"Terrain 1\",\"note\":\"a\\\"b\\nc\"}",
                NotificationService.toJson(params));
    }

    @Test
    void json_sans_parametres_donne_un_objet_vide() {
        assertEquals("{}", NotificationService.toJson(Map.of()));
        assertEquals("{}", NotificationService.toJson(null));
    }
}