package com.footbooking.booking;

import com.footbooking.booking.BookingConfirmation.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

class BookingConfirmationTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
    private static final Instant START = Instant.parse("2026-10-10T19:00:00Z");
    private static final Instant END = Instant.parse("2026-10-10T20:00:00Z");

    private final BookingRepository repo = Mockito.mock(BookingRepository.class);
    private final BookingConfirmation confirmation =
            new BookingConfirmation(repo, Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID bookingId = UUID.randomUUID();
    private final UUID fieldId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        Booking b = new Booking();
        b.setFieldId(fieldId);
        b.setStartTime(START);
        b.setEndTime(END);
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
}