package com.footbooking.booking;

import com.footbooking.booking.BookingConfirmation.Result;
import com.footbooking.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingConfirmationTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final Instant START = Instant.parse("2026-10-10T19:00:00Z");
    private static final Instant END = Instant.parse("2026-10-10T20:00:00Z");

    private final BookingRepository repo = Mockito.mock(BookingRepository.class);
    private final NotificationService notifications = Mockito.mock(NotificationService.class);
    private final BookingConfirmation confirmation =
            new BookingConfirmation(repo, notifications, Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID bookingId = UUID.randomUUID();
    private final UUID fieldId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Booking b = new Booking();
        b.setFieldId(fieldId);
        b.setUserId(userId);
        b.setStartTime(START);
        b.setEndTime(END);
        b.setTotalPrice(new BigDecimal("5000.00"));
        when(repo.findById(bookingId)).thenReturn(Optional.of(b));
    }

    @Test
    void confirmPaid_updatesTheBooking() {
        when(repo.confirmPaidBooking(bookingId)).thenReturn(1);

        assertEquals(Result.CONFIRMED, confirmation.confirmPaid(bookingId));
    }

    @Test
    void confirmPaid_alreadyInAFinalState_changesNothing() {
        when(repo.confirmPaidBooking(bookingId)).thenReturn(0);

        assertEquals(Result.ALREADY_FINAL, confirmation.confirmPaid(bookingId));
    }

    @Test
    void confirmPaid_releasesExpiredHoldsBeforeConfirming() {
        when(repo.confirmPaidBooking(bookingId)).thenReturn(1);

        confirmation.confirmPaid(bookingId);

        InOrder order = inOrder(repo);
        order.verify(repo).cancelExpiredHolds(fieldId, START, END, NOW);
        order.verify(repo).confirmPaidBooking(bookingId);
    }

    @Test
    void confirmPaid_enqueuesTheNotificationWhenConfirmed() {
        when(repo.confirmPaidBooking(bookingId)).thenReturn(1);

        confirmation.confirmPaid(bookingId);

        verify(notifications).enqueue(
                userId,
                "booking_confirmed",
                Map.of("start", START.toString(),
                        "end", END.toString(),
                        "price", "5000.00",
                        "currency", "XOF"),
                "booking-" + bookingId + "-confirmed");
    }

    @Test
    void confirmPaid_enqueuesNothingWhenAlreadyFinal() {
        when(repo.confirmPaidBooking(bookingId)).thenReturn(0);

        confirmation.confirmPaid(bookingId);

        verify(notifications, never()).enqueue(any(), anyString(), anyMap(), anyString());
    }
}