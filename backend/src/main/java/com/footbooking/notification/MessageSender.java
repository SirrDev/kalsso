package com.footbooking.notification;

import java.util.Map;

public interface MessageSender {

    /**
     * Envoie un message. Retourne normalement si le fournisseur accepte,
     * lance MessageSendException sinon.
     */
    void send(NotificationChannel channel, String recipient,
              String template, Map<String, String> params);
}