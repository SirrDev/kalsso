package com.footbooking.booking;

import com.footbooking.field.Field;
import com.footbooking.field.FieldService;
import com.footbooking.notification.NotificationService;
import com.footbooking.venue.VenueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Service
public class ReminderService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    private final BookingRepository bookings;
    private final FieldService fieldService;
    private final VenueService venueService;
    private final NotificationService notifications;
    private final Clock clock;

    public ReminderService(BookingRepository bookings, FieldService fieldService, VenueService venueService,
                           NotificationService notifications, Clock clock) {
        this.bookings = bookings;
        this.fieldService = fieldService;
        this.venueService = venueService;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Cree les rappels dus. Idempotent grace a la cle de dedoublonnage. Renvoie le nombre cree. */
    @Transactional
    public int createDueReminders() {
        Instant now = clock.instant();
        return remind(now.plus(23, ChronoUnit.HOURS), now.plus(24, ChronoUnit.HOURS), "24h")
                + remind(now.plus(1, ChronoUnit.HOURS), now.plus(2, ChronoUnit.HOURS), "2h");
    }

    private int remind(Instant from, Instant to, String label) {
        int created = 0;
        for (Booking b : bookings.findConfirmedStartingBetween(from, to)) {
            Field field = fieldService.getAny(b.getFieldId());
            ZoneId zone = ZoneId.of(venueService.getById(field.getVenueId()).getTimezone());
            var local = b.getStartTime().atZone(zone);
            boolean added = notifications.enqueue(b.getUserId(), "booking_reminder_" + label,
                    Map.of("date", DAY.format(local), "hour", HOUR.format(local), "field", field.getName()),
                    "booking-" + b.getId() + "-reminder-" + label);
            if (added) {
                created++;
            }
        }
        return created;
    }
}