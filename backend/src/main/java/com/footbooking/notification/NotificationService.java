package com.footbooking.notification;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * Ecrit une notification PENDING dans la transaction de l'evenement metier.
     * MANDATORY : appelee hors transaction, elle echoue au lieu d'ecrire a part.
     * Retourne false (sans rien ecrire) si la cle existe deja.
     * Le destinataire (telephone) n'est pas stocke : le worker le resout via userId.
     * Le canal WHATSAPP est le canal prefere ; le repli SMS sera decide en B6-4.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean enqueue(UUID userId, String template, Map<String, String> params, String dedupeKey) {
        if (userId == null) {
            throw new IllegalArgumentException("userId obligatoire");
        }
        if (template == null || template.isBlank() || template.length() > 60) {
            throw new IllegalArgumentException("modele invalide");
        }
        if (dedupeKey == null || dedupeKey.isBlank() || dedupeKey.length() > 120) {
            throw new IllegalArgumentException("dedupeKey invalide");
        }
        // L'index unique reste le filet de securite ; ce test evite d'abandonner la transaction metier
        if (notifications.existsByDedupeKey(dedupeKey)) {
            return false;
        }
        Notification n = new Notification();
        n.setUserId(userId);
        n.setChannel(NotificationChannel.WHATSAPP);
        n.setTemplate(template);
        n.setPayload(toJson(params));
        n.setSendAt(clock.instant());
        n.setDedupeKey(dedupeKey);
        notifications.save(n);
        return true;
    }

    static String toJson(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(e.getKey())).append("\":\"")
                    .append(escape(e.getValue() == null ? "" : e.getValue())).append('"');
        }
        return sb.append('}').toString();
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}