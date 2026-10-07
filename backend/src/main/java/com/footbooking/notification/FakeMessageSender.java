package com.footbooking.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

// Developpement local et tests UNIQUEMENT : jamais en production.
@Component
@ConditionalOnProperty(name = "app.notification.sender", havingValue = "fake")
public class FakeMessageSender implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(FakeMessageSender.class);

    private final String mode;

    public FakeMessageSender(@Value("${app.notification.fake-mode:accepted}") String mode) {
        if (!mode.equals("accepted") && !mode.equals("rejected")) {
            throw new IllegalStateException(
                    "app.notification.fake-mode doit valoir accepted ou rejected");
        }
        this.mode = mode;
    }

    @Override
    public void send(NotificationChannel channel, String recipient,
                     String template, Map<String, String> params) {
        // Jamais le numero ni le contenu dans les logs
        log.info("[FAKE] envoi {} modele={} mode={}", channel, template, mode);
        if (mode.equals("rejected")) {
            throw new MessageSendException("Envoi refuse par le faux fournisseur");
        }
    }
}