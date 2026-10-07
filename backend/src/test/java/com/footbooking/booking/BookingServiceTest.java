package com.footbooking.booking;

import com.footbooking.availability.AvailabilityService;
import com.footbooking.availability.AvailabilityService.BookableSlot;
import com.footbooking.booking.BookingDtos.BookingResponse;
import com.footbooking.common.ApiException;
import com.footbooking.notification.NotificationService;
import com.footbooking.payment.PaymentRefundRequests;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant START = Instant.parse("2026-10-10T19:00:00Z");
    private static final Instant END = Instant.parse("2026-10-10T20:00:00Z");

    private final BookingRepository repo = Mockito.mock(BookingRepository.class);
    private final AvailabilityService availability = Mockito.mock(AvailabilityService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final PaymentRefundRequests refunds = Mockito.mock(PaymentRefundRequests.class);
    private final NotificationService notifications = Mockito.mock(NotificationService.class);
    private final BookingService service =
            new BookingService(repo, availability, refunds, notifications, clock, 10, 24);
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

    private Booking booking(BookingStatus status, Instant start) {
        Booking b = new Booking();
        b.setUserId(userId);
        b.setFieldId(fieldId);
        b.setStartTime(start);
        b.setEndTime(start.plusSeconds(3600));
        b.setStatus(status);
        b.setTotalPrice(new BigDecimal("8000"));
        return b;
    }

    private void owns(UUID bookingId, Booking b) {
        when(repo.findByIdAndUserId(bookingId, userId)).thenReturn(Optional.of(b));
    }

    @Test
    void cancel_confirmedMoreThan24hBefore_isCancelled() {
        UUID id = UUID.randomUUID();
        owns(id, booking(BookingStatus.CONFIRMED, START));   // START = 9 jours apres NOW

        assertEquals(BookingStatus.CANCELLED, service.cancel(userId, id).status());
    }

    @Test
    void cancel_confirmedExactly24hBefore_isAllowed() {
        UUID id = UUID.randomUUID();
        owns(id, booking(BookingStatus.CONFIRMED, NOW.plusSeconds(24 * 3600L)));

        assertEquals(BookingStatus.CANCELLED, service.cancel(userId, id).status());
    }

    @Test
    void cancel_confirmedLessThan24hBefore_returns409() {
        UUID id = UUID.randomUUID();
        owns(id, booking(BookingStatus.CONFIRMED, NOW.plusSeconds(23 * 3600L)));

        ApiException ex = assertThrows(ApiException.class, () -> service.cancel(userId, id));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void cancel_pendingHold_isAllowedEvenWithin24h() {
        UUID id = UUID.randomUUID();
        owns(id, booking(BookingStatus.PENDING, NOW.plusSeconds(2 * 3600L)));

        assertEquals(BookingStatus.CANCELLED, service.cancel(userId, id).status());
    }

    @Test
    void cancel_alreadyCancelled_returns409() {
        UUID id = UUID.randomUUID();
        owns(id, booking(BookingStatus.CANCELLED, START));

        ApiException ex = assertThrows(ApiException.class, () -> service.cancel(userId, id));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void cancel_completedBooking_returns409() {
        UUID id = UUID.randomUUID();
        owns(id, booking(BookingStatus.COMPLETED, START));

        ApiException ex = assertThrows(ApiException.class, () -> service.cancel(userId, id));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void cancel_someoneElsesBooking_returns404() {
        UUID id = UUID.randomUUID();
        when(repo.findByIdAndUserId(id, userId)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> service.cancel(userId, id));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void listMine_returnsTheUsersBookings() {
        when(repo.findTop100ByUserIdOrderByStartTimeDesc(userId))
                .thenReturn(List.of(booking(BookingStatus.PENDING, START), booking(BookingStatus.CANCELLED, START)));

        assertEquals(2, service.listMine(userId).size());
    }

    @Test
    void cancel_confirmedBooking_requestsARefund() {
        UUID id = UUID.randomUUID();
        owns(id, booking(BookingStatus.CONFIRMED, START));

        service.cancel(userId, id);

        verify(refunds).requestRefundFor(any());
    }

    @Test
    void cancel_pendingHold_requestsNoRefund() {
        UUID id = UUID.randomUUID();
        owns(id, booking(BookingStatus.PENDING, NOW.plusSeconds(2 * 3600L)));

        service.cancel(userId, id);

        verify(refunds, never()).requestRefundFor(any());
    }

    @Test
    void cancel_confirmedBooking_enqueuesACancellationNotification() {
        UUID id = UUID.randomUUID();
        owns(id, booking(BookingStatus.CONFIRMED, START));

        service.cancel(userId, id);

        verify(notifications).enqueue(eq(userId), eq("booking_cancelled"), anyMap(), anyString());
    }

    @Test
    void cancel_pendingHold_enqueuesNoNotification() {
        UUID id = UUID.randomUUID();
        owns(id, booking(BookingStatus.PENDING, NOW.plusSeconds(2 * 3600L)));

        service.cancel(userId, id);

        verify(notifications, never()).enqueue(any(), anyString(), anyMap(), anyString());
    }

    @Test
    void findUserId_returnsTheClientOfTheBooking() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(booking(BookingStatus.CONFIRMED, START)));

        assertEquals(Optional.of(userId), service.findUserId(id));
    }

    @Test
    void findUserId_isEmptyForAnOwnerBlock() {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(new Booking()));

        assertTrue(service.findUserId(id).isEmpty());
    }
}