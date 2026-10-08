package com.footbooking.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnExpression("'${app.notification.sender:}' != ''")
public class NotificationJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationJob.class);

    private final NotificationDispatcher dispatcher;

    public NotificationJob(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /** Non transactionnel : chaque envoi passe par le proxy du dispatcher et a sa propre transaction. */
    @Scheduled(fixedDelayString = "${app.notification.poll-ms:30000}")
    public void run() {
        for (UUID id : dispatcher.dueIds()) {
            try {
                dispatcher.dispatch(id);
            } catch (RuntimeException e) {
                // Jamais le message : il pourrait contenir un numero
                log.error("Echec inattendu de la notification {} : {}", id, e.getClass().getSimpleName());
            }
        }
    }
}