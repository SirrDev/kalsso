package com.footbooking.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RefundJob {

    private static final Logger log = LoggerFactory.getLogger(RefundJob.class);

    private final RefundService service;

    public RefundJob(RefundService service) {
        this.service = service;
    }

    /** Non transactionnel : chaque tentative passe par le proxy du service et a sa propre transaction. */
    @Scheduled(fixedDelayString = "${app.payment.refund.poll-ms:60000}")
    public void run() {
        for (UUID id : service.dueIds()) {
            try {
                service.attempt(id, false);
            } catch (RuntimeException e) {
                log.error("Echec inattendu du remboursement {} : {}", id, e.getMessage());
            }
        }
    }
}