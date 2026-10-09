package com.footbooking.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

// Developpement local et tests UNIQUEMENT : jamais en production.
@Component
@ConditionalOnProperty(name = "app.notification.sender", havingValue = "fake")
public class FakeMessageSender implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(FakeMessageSender.class);
    private static final Set<String> MODES = Set.of("accepted", "rejected", "whatsapp-rejected");

    private final String mode;

    public FakeMessageSender(@Value("${app.notification.fake-mode:accepted}") String mode) {
        if (!MODES.contains(mode)) {
            throw new IllegalStateException(
                    "app.notification.fake-mode doit valoir accepted, rejected ou whatsapp-rejected");
        }
        this.mode = mode;
    }

    @Override
    public void send(NotificationChannel channel, String recipient,
                     String template, Map<String, String> params) {
        // Jamais le numero ni le contenu dans les logs
        log.info("[FAKE] envoi {} modele={} mode={}", channel, template, mode);
        boolean refuse = mode.equals("rejected")
                || (mode.equals("whatsapp-rejected") && channel == NotificationChannel.WHATSAPP);
        if (refuse) {
            throw new MessageSendException("Envoi refuse par le faux fournisseur");
        }
    }
}