package com.footbooking.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReminderJob {

    private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

    private final ReminderService reminders;

    public ReminderJob(ReminderService reminders) {
        this.reminders = reminders;
    }

    /** Non transactionnel : le service porte la transaction. fixedDelay : pas de chevauchement. */
    @Scheduled(fixedDelayString = "${app.booking.reminder-poll-ms:300000}")
    public void run() {
        try {
            int created = reminders.createDueReminders();
            if (created > 0) {
                log.info("{} rappel(s) de reservation mis en file", created);
            }
        } catch (RuntimeException e) {
            // Jamais le message : il pourrait contenir des donnees personnelles
            log.error("Echec de la creation des rappels : {}", e.getClass().getSimpleName());
        }
    }
}