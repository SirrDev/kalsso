package com.footbooking.notification;

import com.footbooking.auth.UserContact;
import com.footbooking.auth.UserContactLookup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Actif seulement si un MessageSender est configure (app.notification.sender non vide)
@Service
@ConditionalOnExpression("'${app.notification.sender:}' != ''")
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    static final int BATCH_SIZE = 20;

    public enum Outcome { SENT, RETRY_LATER, FAILED, SKIPPED }

    private final NotificationRepository notifications;
    private final MessageSender sender;
    private final UserContactLookup contacts;
    private final Clock clock;
    private final int maxAttempts;
    private final long retryMinutes;

    public NotificationDispatcher(NotificationRepository notifications, MessageSender sender,
                                  UserContactLookup contacts, Clock clock,
                                  @Value("${app.notification.max-attempts:5}") int maxAttempts,
                                  @Value("${app.notification.retry-minutes:5}") long retryMinutes) {
        this.notifications = notifications;
        this.sender = sender;
        this.contacts = contacts;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.retryMinutes = retryMinutes;
    }

    @Transactional(readOnly = true)
    public List<UUID> dueIds() {
        return notifications.findDueIds(clock.instant(), PageRequest.of(0, BATCH_SIZE));
    }

    /** Une notification, dans sa propre transaction (appelee depuis un autre bean). */
    @Transactional
    public Outcome dispatch(UUID id) {
        Notification n = notifications.findForUpdateById(id).orElse(null);
        if (n == null || n.getStatus() != NotificationStatus.PENDING) {
            return Outcome.SKIPPED;
        }
        Instant now = clock.instant();
        if (n.getSendAt().isAfter(now)) {
            return Outcome.SKIPPED;
        }

        n.setAttempts(n.getAttempts() + 1);

        Optional<UserContact> contact = n.getUserId() == null
                ? Optional.empty() : contacts.find(n.getUserId());
        String phone = contact.map(UserContact::phone).orElse(null);
        if (phone == null || phone.isBlank()) {
            // Pas de destinataire : reessayer ne servirait a rien
            return fail(n, "destinataire sans telephone");
        }

        // Un repli deja decide (canal SMS en base) n'est jamais remis en WhatsApp
        if (n.getChannel() == NotificationChannel.WHATSAPP
                && !contact.map(UserContact::whatsappOptIn).orElse(false)) {
            n.setChannel(NotificationChannel.SMS);
        }

        try {
            sender.send(n.getChannel(), phone, n.getTemplate(), PayloadJson.parse(n.getPayload()));
        } catch (RuntimeException e) {
            // Jamais le message de l'exception : il pourrait contenir un numero
            log.warn("Envoi {} par {} echoue (tentative {}) : {}",
                    n.getId(), n.getChannel(), n.getAttempts(), e.getClass().getSimpleName());
            if (n.getChannel() == NotificationChannel.WHATSAPP) {
                // Repli : un seul echec WhatsApp, nouvel essai immediat en SMS
                n.setChannel(NotificationChannel.SMS);
                n.setLastError("echec WhatsApp, repli SMS");
                n.setSendAt(now);
                return Outcome.RETRY_LATER;
            }
            if (n.getAttempts() >= maxAttempts) {
                return fail(n, "echec d'envoi apres " + n.getAttempts() + " tentatives");
            }
            n.setLastError("echec d'envoi, nouvel essai prevu");
            n.setSendAt(now.plus(retryMinutes * n.getAttempts(), ChronoUnit.MINUTES));
            return Outcome.RETRY_LATER;
        }

        n.setStatus(NotificationStatus.SENT);
        n.setSentAt(now);
        n.setLastError(null);
        return Outcome.SENT;
    }

    private Outcome fail(Notification n, String reason) {
        n.setStatus(NotificationStatus.FAILED);
        n.setLastError(reason);
        log.error("NOTIFICATION ABANDONNEE : {} modele={} raison={}", n.getId(), n.getTemplate(), reason);
        return Outcome.FAILED;
    }
}