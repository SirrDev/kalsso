package com.footbooking.payment;

import com.footbooking.booking.BookingService;
import com.footbooking.common.ApiException;
import com.footbooking.notification.NotificationService;
import com.footbooking.payment.PaymentProvider.RefundResult;
import com.footbooking.payment.RefundService.Outcome;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    private final PaymentRepository payments = Mockito.mock(PaymentRepository.class);
    private final PaymentProvider provider = Mockito.mock(PaymentProvider.class);
    private final BookingService bookingService = Mockito.mock(BookingService.class);
    private final NotificationService notifications = Mockito.mock(NotificationService.class);
    private final RefundService service =
            new RefundService(payments, provider, bookingService, notifications,
                    Clock.fixed(NOW, ZoneOffset.UTC), 3, 10);
    private final UUID id = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    private Payment pending(int attempts, Instant lastAttempt) {
        Payment p = new Payment();
        p.setProviderRef("a1b2c3d4e5f60718a1b2c3d4e5f60718");
        p.setStatus(PaymentStatus.REFUND_PENDING);
        p.setAmount(new BigDecimal("5000.00"));
        p.setRefundAttempts(attempts);
        p.setRefundLastAttemptAt(lastAttempt);
        when(payments.findForUpdateById(id)).thenReturn(Optional.of(p));
        when(bookingService.findUserId(any())).thenReturn(Optional.of(userId));
        return p;
    }

    private void providerAnswers(boolean accepted) {
        when(provider.refund(any())).thenReturn(new RefundResult(accepted, accepted ? "ok" : "refus"));
    }

    @Test
    void accepted_marksThePaymentRefunded() {
        Payment p = pending(0, null);
        providerAnswers(true);

        assertEquals(Outcome.REFUNDED, service.attempt(id, false));
        assertEquals(PaymentStatus.REFUNDED, p.getStatus());
        assertEquals(NOW, p.getRefundedAt());
        assertEquals(1, p.getRefundAttempts());
        assertEquals(NOW, p.getRefundLastAttemptAt());
    }

    @Test
    void refused_staysPendingAndCountsTheAttempt() {
        Payment p = pending(0, null);
        providerAnswers(false);

        assertEquals(Outcome.RETRY_LATER, service.attempt(id, false));
        assertEquals(PaymentStatus.REFUND_PENDING, p.getStatus());
        assertEquals(1, p.getRefundAttempts());
        assertNull(p.getRefundedAt());
    }

    @Test
    void providerUnavailable_staysPendingAndCountsTheAttempt() {
        Payment p = pending(0, null);
        when(provider.refund(any())).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "indisponible"));

        assertEquals(Outcome.RETRY_LATER, service.attempt(id, false));
        assertEquals(PaymentStatus.REFUND_PENDING, p.getStatus());
        assertEquals(1, p.getRefundAttempts());
    }

    @Test
    void unexpectedException_isTreatedAsAFailedAttempt() {
        Payment p = pending(0, null);
        when(provider.refund(any())).thenThrow(new IllegalStateException("boom"));

        assertEquals(Outcome.RETRY_LATER, service.attempt(id, false));
        assertEquals(1, p.getRefundAttempts());
    }

    @Test
    void lastAllowedAttempt_givesUpButKeepsThePaymentVisible() {
        Payment p = pending(2, null);
        providerAnswers(false);

        assertEquals(Outcome.GAVE_UP, service.attempt(id, false));
        assertEquals(3, p.getRefundAttempts());
        assertEquals(PaymentStatus.REFUND_PENDING, p.getStatus());
    }

    @Test
    void notDueYet_isSkippedWithoutCallingTheProvider() {
        Payment p = pending(1, NOW.minusSeconds(300));   // 5 min : delai de 10 min pas ecoule

        assertEquals(Outcome.SKIPPED, service.attempt(id, false));
        assertEquals(1, p.getRefundAttempts());
        verify(provider, never()).refund(any());
    }

    @Test
    void dueExactlyAfterTheDelay_isAttempted() {
        pending(1, NOW.minusSeconds(600));
        providerAnswers(true);

        assertEquals(Outcome.REFUNDED, service.attempt(id, false));
    }

    @Test
    void maxAttemptsReached_isSkippedByTheAutomaticJob() {
        pending(3, null);

        assertEquals(Outcome.SKIPPED, service.attempt(id, false));
        verify(provider, never()).refund(any());
    }

    @Test
    void force_overridesTheDelayAndTheAttemptLimit() {
        Payment p = pending(3, NOW.minusSeconds(60));
        providerAnswers(true);

        assertEquals(Outcome.REFUNDED, service.attempt(id, true));
        assertEquals(PaymentStatus.REFUNDED, p.getStatus());
        assertEquals(4, p.getRefundAttempts());
    }

    @Test
    void alreadyRefunded_isSkipped_evenWhenForced() {
        Payment p = pending(0, null);
        p.setStatus(PaymentStatus.REFUNDED);

        assertEquals(Outcome.SKIPPED, service.attempt(id, true));
        verify(provider, never()).refund(any());
    }

    @Test
    void unknownPayment_isSkipped() {
        when(payments.findForUpdateById(id)).thenReturn(Optional.empty());

        assertEquals(Outcome.SKIPPED, service.attempt(id, false));
    }

    @Test
    void dueIds_usesTheRetryWindow() {
        when(payments.findRefundDueIds(eq(3), eq(NOW.minusSeconds(600)), any(Pageable.class)))
                .thenReturn(List.of(id));

        assertEquals(List.of(id), service.dueIds());
    }

    @Test
    void accepted_enqueuesARefundNotification() {
        pending(0, null);
        providerAnswers(true);

        service.attempt(id, false);

        verify(notifications).enqueue(eq(userId), eq("payment_refunded"), anyMap(), anyString());
    }

    @Test
    void refusedOrGivenUp_enqueuesNoNotification() {
        pending(2, null);
        providerAnswers(false);

        service.attempt(id, false);

        verify(notifications, never()).enqueue(any(), anyString(), anyMap(), anyString());
    }
}