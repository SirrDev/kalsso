package com.footbooking.notification;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FakeMessageSenderTest {

    private static final Map<String, String> PARAMS = Map.of("terrain", "Terrain 1");

    @Test
    void modeAccepted_nelancePasDexception() {
        FakeMessageSender sender = new FakeMessageSender("accepted");
        assertDoesNotThrow(() ->
                sender.send(NotificationChannel.SMS, "+221700000000", "booking_confirmed", PARAMS));
    }

    @Test
    void modeRejected_lanceUneException() {
        FakeMessageSender sender = new FakeMessageSender("rejected");
        assertThrows(MessageSendException.class, () ->
                sender.send(NotificationChannel.WHATSAPP, "+221700000000", "booking_confirmed", PARAMS));
    }

    @Test
    void modeInvalide_fait_echouer_la_construction() {
        assertThrows(IllegalStateException.class, () -> new FakeMessageSender("n-importe-quoi"));
    }

    @Test
    void notificationParDefaut_estPendingAvecZeroTentative() {
        Notification n = new Notification();
        org.junit.jupiter.api.Assertions.assertEquals(NotificationStatus.PENDING, n.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(0, n.getAttempts());
        org.junit.jupiter.api.Assertions.assertEquals("{}", n.getPayload());
    }
}