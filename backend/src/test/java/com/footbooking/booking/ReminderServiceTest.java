package com.footbooking.booking;

import com.footbooking.field.Field;
import com.footbooking.field.FieldService;
import com.footbooking.notification.NotificationService;
import com.footbooking.venue.Venue;
import com.footbooking.venue.VenueService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReminderServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    private final BookingRepository repo = Mockito.mock(BookingRepository.class);
    private final FieldService fieldService = Mockito.mock(FieldService.class);
    private final VenueService venueService = Mockito.mock(VenueService.class);
    private final NotificationService notifications = Mockito.mock(NotificationService.class);
    private final ReminderService service = new ReminderService(repo, fieldService, venueService,
            notifications, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID userId = UUID.randomUUID();

    private Booking booking(Instant start) {
        Booking b = new Booking();
        b.setUserId(userId);
        b.setFieldId(UUID.randomUUID());
        b.setStartTime(start);
        b.setEndTime(start.plusSeconds(3600));
        Field f = new Field();
        f.setName("Terrain 1");
        f.setVenueId(UUID.randomUUID());
        Venue v = new Venue();
        v.setTimezone("Europe/Paris");
        when(fieldService.getAny(any())).thenReturn(f);
        when(venueService.getById(any())).thenReturn(v);
        return b;
    }

    @Test
    void reminder2h_isEnqueuedWithLocalTime() {
        Booking b = booking(NOW.plusSeconds(90 * 60));   // 11:30 UTC = 13:30 a Paris (UTC+2)
        when(repo.findConfirmedStartingBetween(NOW.plusSeconds(3600), NOW.plusSeconds(7200)))
                .thenReturn(List.of(b));
        when(notifications.enqueue(any(), anyString(), anyMap(), anyString())).thenReturn(true);

        assertEquals(1, service.createDueReminders());
        verify(notifications).enqueue(eq(userId), eq("booking_reminder_2h"),
                eq(Map.of("date", "19/10/2026", "hour", "13:30", "field", "Terrain 1")),
                eq("booking-" + b.getId() + "-reminder-2h"));
    }

    @Test
    void alreadyEnqueued_isNotCounted() {
        Booking b = booking(NOW.plusSeconds(90 * 60));
        when(repo.findConfirmedStartingBetween(NOW.plusSeconds(3600), NOW.plusSeconds(7200)))
                .thenReturn(List.of(b));
        when(notifications.enqueue(any(), anyString(), anyMap(), anyString())).thenReturn(false);

        assertEquals(0, service.createDueReminders());
    }

    @Test
    void noBookingDue_enqueuesNothing() {
        assertEquals(0, service.createDueReminders());
        verify(notifications, never()).enqueue(any(), anyString(), anyMap(), anyString());
    }
}