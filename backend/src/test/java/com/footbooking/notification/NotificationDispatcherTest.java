package com.footbooking.notification;

import com.footbooking.auth.UserContact;
import com.footbooking.auth.UserContactLookup;
import com.footbooking.notification.NotificationDispatcher.Outcome;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    private final NotificationRepository repo = Mockito.mock(NotificationRepository.class);
    private final MessageSender sender = Mockito.mock(MessageSender.class);
    private final UserContactLookup contacts = Mockito.mock(UserContactLookup.class);
    private final NotificationDispatcher dispatcher =
            new NotificationDispatcher(repo, sender, contacts, Clock.fixed(NOW, ZoneOffset.UTC), 3, 5);
    private final UUID id = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private Notification pending(int attempts, Instant sendAt) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setChannel(NotificationChannel.WHATSAPP);
        n.setTemplate("booking_confirmed");
        n.setPayload("{\"price\": \"5000.00\"}");
        n.setAttempts(attempts);
        n.setSendAt(sendAt);
        when(repo.findForUpdateById(id)).thenReturn(Optional.of(n));
        when(contacts.find(userId)).thenReturn(Optional.of(new UserContact("+221700000000", true)));
        return n;
    }

    @Test
    void success_marksSent() {
        Notification n = pending(0, NOW.minusSeconds(10));

        assertEquals(Outcome.SENT, dispatcher.dispatch(id));
        assertEquals(NotificationStatus.SENT, n.getStatus());
        assertEquals(NOW, n.getSentAt());
        assertEquals(1, n.getAttempts());
        assertNull(n.getLastError());
        verify(sender).send(eq(NotificationChannel.WHATSAPP), eq("+221700000000"),
                eq("booking_confirmed"), eq(Map.of("price", "5000.00")));
    }

    @Test
    void failure_staysPendingAndPostponesTheNextTry() {
        Notification n = pending(0, NOW.minusSeconds(10));
        doThrow(new MessageSendException("refus")).when(sender).send(any(), anyString(), anyString(), anyMap());

        assertEquals(Outcome.RETRY_LATER, dispatcher.dispatch(id));
        assertEquals(NotificationStatus.PENDING, n.getStatus());
        assertEquals(1, n.getAttempts());
        assertEquals(NOW.plusSeconds(5 * 60), n.getSendAt());
        assertNotNull(n.getLastError());
    }

    @Test
    void delayGrowsWithTheAttemptCount() {
        Notification n = pending(1, NOW.minusSeconds(10));
        doThrow(new MessageSendException("refus")).when(sender).send(any(), anyString(), anyString(), anyMap());

        dispatcher.dispatch(id);

        assertEquals(2, n.getAttempts());
        assertEquals(NOW.plusSeconds(2 * 5 * 60), n.getSendAt());
    }

    @Test
    void lastAllowedAttempt_marksFailed() {
        Notification n = pending(2, NOW.minusSeconds(10));
        doThrow(new MessageSendException("refus")).when(sender).send(any(), anyString(), anyString(), anyMap());

        assertEquals(Outcome.FAILED, dispatcher.dispatch(id));
        assertEquals(NotificationStatus.FAILED, n.getStatus());
        assertEquals(3, n.getAttempts());
    }

    @Test
    void errorMessage_neverContainsThePhoneNumber() {
        Notification n = pending(2, NOW.minusSeconds(10));
        doThrow(new MessageSendException("echec pour +221700000000")).when(sender)
                .send(any(), anyString(), anyString(), anyMap());

        dispatcher.dispatch(id);

        assertFalse(n.getLastError().contains("221700000000"));
    }

    @Test
    void missingPhone_failsWithoutCallingTheSender() {
        Notification n = pending(0, NOW.minusSeconds(10));
        when(contacts.find(userId)).thenReturn(Optional.of(new UserContact(null, true)));

        assertEquals(Outcome.FAILED, dispatcher.dispatch(id));
        assertEquals(NotificationStatus.FAILED, n.getStatus());
        verify(sender, never()).send(any(), anyString(), anyString(), anyMap());
    }

    @Test
    void alreadySent_isSkipped() {
        Notification n = pending(1, NOW.minusSeconds(10));
        n.setStatus(NotificationStatus.SENT);

        assertEquals(Outcome.SKIPPED, dispatcher.dispatch(id));
        verify(sender, never()).send(any(), anyString(), anyString(), anyMap());
    }

    @Test
    void notDueYet_isSkipped() {
        pending(0, NOW.plusSeconds(60));

        assertEquals(Outcome.SKIPPED, dispatcher.dispatch(id));
        verify(sender, never()).send(any(), anyString(), anyString(), anyMap());
    }

    @Test
    void unknownNotification_isSkipped() {
        when(repo.findForUpdateById(id)).thenReturn(Optional.empty());

        assertEquals(Outcome.SKIPPED, dispatcher.dispatch(id));
    }

    @Test
    void dueIds_asksForPendingNotificationsDueNow() {
        when(repo.findDueIds(eq(NOW), any(Pageable.class))).thenReturn(List.of(id));

        assertEquals(List.of(id), dispatcher.dueIds());
    }
}