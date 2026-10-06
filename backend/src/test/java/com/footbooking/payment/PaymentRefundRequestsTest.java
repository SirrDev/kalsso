package com.footbooking.payment;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class PaymentRefundRequestsTest {

    private final PaymentRepository payments = Mockito.mock(PaymentRepository.class);
    private final PaymentRefundRequests requests = new PaymentRefundRequests(payments);
    private final UUID bookingId = UUID.randomUUID();

    private static Payment paid(int attempts) {
        Payment p = new Payment();
        p.setStatus(PaymentStatus.SUCCEEDED);
        p.setRefundAttempts(attempts);
        return p;
    }

    @Test
    void marksEverySucceededPaymentOfTheBookingForRefund() {
        Payment a = paid(2);
        Payment b = paid(1);
        when(payments.findByBookingIdAndStatus(bookingId, PaymentStatus.SUCCEEDED)).thenReturn(List.of(a, b));

        assertEquals(2, requests.requestRefundFor(bookingId));
        assertEquals(PaymentStatus.REFUND_PENDING, a.getStatus());
        assertEquals(PaymentStatus.REFUND_PENDING, b.getStatus());
        assertEquals(0, a.getRefundAttempts());
        assertEquals(0, b.getRefundAttempts());
    }

    @Test
    void noPayment_isANoop() {
        when(payments.findByBookingIdAndStatus(bookingId, PaymentStatus.SUCCEEDED)).thenReturn(List.of());

        assertEquals(0, requests.requestRefundFor(bookingId));
    }
}