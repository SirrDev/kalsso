package com.footbooking.booking;

import com.footbooking.availability.AvailabilityService;
import com.footbooking.availability.AvailabilityService.BookableSlot;
import com.footbooking.booking.BookingDtos.BookingResponse;
import com.footbooking.common.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

class BookingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant START = Instant.parse("2026-10-10T19:00:00Z");
    private static final Instant END = Instant.parse("2026-10-10T20:00:00Z");

    private final BookingRepository repo = Mockito.mock(BookingRepository.class);
    private final AvailabilityService availability = Mockito.mock(AvailabilityService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final BookingService service = new BookingService(repo, availability, clock, 10);

    private final UUID userId = UUID.randomUUID();
    private final UUID fieldId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(availability.requireBookable(fieldId, START))
                .thenReturn(new BookableSlot(fieldId, START, END, new BigDecimal("8000"), "XOF"));
    }

    private static DataIntegrityViolationException violation(String detail) {
        return new DataIntegrityViolationException("could not execute statement", new RuntimeException(detail));
    }

    @Test
    void create_valid_isPendingWithTenMinuteHold() {
        when(repo.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        BookingResponse res = service.create(userId, fieldId, START);

        assertEquals(BookingStatus.PENDING, res.status());
        assertEquals(Instant.parse("2026-10-01T12:10:00Z"), res.expiresAt());
        assertEquals(new BigDecimal("8000"), res.totalPrice());
        assertEquals(START, res.start());
        assertEquals(END, res.end());
    }

    @Test
    void create_overlap_returns409() {
        when(repo.saveAndFlush(any(Booking.class)))
                .thenThrow(violation("conflicting key value violates exclusion constraint \"booking_no_overlap\""));

        ApiException ex = assertThrows(ApiException.class, () -> service.create(userId, fieldId, START));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void create_otherIntegrityViolation_isNotHiddenAs409() {
        when(repo.saveAndFlush(any(Booking.class)))
                .thenThrow(violation("violates foreign key constraint \"booking_user_id_fkey\""));

        assertThrows(DataIntegrityViolationException.class, () -> service.create(userId, fieldId, START));
    }

    @Test
    void create_releasesExpiredHoldsBeforeInserting() {
        when(repo.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(userId, fieldId, START);

        InOrder order = inOrder(repo);
        order.verify(repo).cancelExpiredHolds(fieldId, START, END, NOW);
        order.verify(repo).saveAndFlush(any(Booking.class));
    }

    @Test
    void isOverlap_detectsTheConstraintName() {
        assertTrue(BookingService.isOverlap(violation("... \"booking_no_overlap\" ...")));
    }

    @Test
    void isOverlap_ignoresOtherViolations() {
        assertFalse(BookingService.isOverlap(violation("duplicate key value violates unique constraint")));
    }

    @Test
    void releaseExpiredHolds_usesTheClockAndReturnsTheCount() {
        when(repo.cancelAllExpiredHolds(NOW)).thenReturn(3);

        assertEquals(3, service.releaseExpiredHolds());
        verify(repo).cancelAllExpiredHolds(NOW);
    }
}