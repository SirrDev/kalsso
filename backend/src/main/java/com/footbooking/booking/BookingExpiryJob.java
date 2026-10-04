package com.footbooking.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BookingExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(BookingExpiryJob.class);

    private final BookingService bookingService;

    public BookingExpiryJob(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** fixedDelay : la prochaine execution attend la fin de la precedente. */
    @Scheduled(fixedDelayString = "${app.booking.cleanup-ms:60000}")
    public void releaseExpiredHolds() {
        int released = bookingService.releaseExpiredHolds();
        if (released > 0) {
            log.info("{} blocage(s) de reservation expire(s) annule(s)", released);
        }
    }
}