package com.footbooking.payment;

import com.footbooking.booking.BookingConfirmation;
import com.footbooking.common.ApiException;
import com.footbooking.payment.PaymentIpnService.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentIpnServiceTest {

    private static final String KEY = "test-key";
    private static final String SECRET = "test-secret";
    private static final String REF = "a1b2c3d4e5f60718a1b2c3d4e5f60718";
    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    private final PaymentRepository payments = Mockito.mock(PaymentRepository.class);
    private final BookingConfirmation confirmation = Mockito.mock(BookingConfirmation.class);
    private final PaymentProvider provider = Mockito.mock(PaymentProvider.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final UUID bookingId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(provider.name()).thenReturn("PAYTECH");
    }

    private PaymentIpnService service(String env) {
        return new PaymentIpnService(payments, confirmation, provider, clock, KEY, SECRET, env);
    }

    private Payment existing(PaymentStatus status) {
        Payment p = new Payment();
        p.setBookingId(bookingId);
        p.setProviderRef(REF);
        p.setAmount(new BigDecimal("8000.00"));
        p.setCurrency("XOF");
        p.setStatus(status);
        when(payments.findForUpdateByProviderAndProviderRef("PAYTECH", REF)).thenReturn(Optional.of(p));
        return p;
    }

    private static Map<String, String> signed(String event, String price) {
        Map<String, String> m = new HashMap<>();
        m.put("type_event", event);
        m.put("ref_command", REF);
        m.put("item_price", price);
        m.put("currency", "XOF");
        m.put("hmac_compute", PayTechSignature.hmacHex(price + "|" + REF + "|" + KEY, SECRET));
        return m;
    }

    private static DataIntegrityViolationException violation(String detail) {
        return new DataIntegrityViolationException("could not execute statement", new RuntimeException(detail));
    }

    @Test
    void invalidSignature_returns403AndReadsNothing() {
        Map<String, String> forged = signed("sale_complete", "8000");
        forged.put("hmac_compute", "deadbeef");

        ApiException ex = assertThrows(ApiException.class, () -> service("test").handle(forged));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(payments, never()).findForUpdateByProviderAndProviderRef(any(), any());
        verify(confirmation, never()).confirmPaid(any());
    }

    @Test
    void saleComplete_confirmsTheBookingAndMarksThePaymentPaid() {
        Payment p = existing(PaymentStatus.INITIATED);

        Outcome outcome = service("prod").handle(signed("sale_complete", "8000"));

        assertEquals(Outcome.CONFIRMED, outcome);
        assertEquals(PaymentStatus.SUCCEEDED, p.getStatus());
        assertEquals(NOW, p.getPaidAt());
        verify(confirmation).confirmPaid(bookingId);
    }

    @Test
    void saleComplete_alreadyProcessed_isADuplicate() {
        existing(PaymentStatus.SUCCEEDED);

        Outcome outcome = service("prod").handle(signed("sale_complete", "8000"));

        assertEquals(Outcome.DUPLICATE, outcome);
        verify(confirmation, never()).confirmPaid(any());
    }

    @Test
    void saleComplete_slotAlreadyTaken_marksRefundPending() {
        Payment p = existing(PaymentStatus.INITIATED);
        when(confirmation.confirmPaid(bookingId))
                .thenThrow(violation("violates exclusion constraint \"booking_no_overlap\""));

        Outcome outcome = service("prod").handle(signed("sale_complete", "8000"));

        assertEquals(Outcome.REFUND_PENDING, outcome);
        assertEquals(PaymentStatus.REFUND_PENDING, p.getStatus());
        assertEquals(NOW, p.getPaidAt());
    }

    @Test
    void saleComplete_otherIntegrityError_isNotHidden() {
        existing(PaymentStatus.INITIATED);
        when(confirmation.confirmPaid(bookingId))
                .thenThrow(violation("violates foreign key constraint \"booking_field_id_fkey\""));

        assertThrows(DataIntegrityViolationException.class,
                () -> service("prod").handle(signed("sale_complete", "8000")));
    }

    @Test
    void saleComplete_wrongAmountInProd_isNotConfirmed() {
        Payment p = existing(PaymentStatus.INITIATED);

        Outcome outcome = service("prod").handle(signed("sale_complete", "100"));

        assertEquals(Outcome.IGNORED, outcome);
        assertEquals(PaymentStatus.INITIATED, p.getStatus());
        verify(confirmation, never()).confirmPaid(any());
    }

    @Test
    void saleComplete_wrongAmountInTestEnvironment_isTolerated() {
        Payment p = existing(PaymentStatus.INITIATED);

        Outcome outcome = service("test").handle(signed("sale_complete", "120"));

        assertEquals(Outcome.CONFIRMED, outcome);
        assertEquals(PaymentStatus.SUCCEEDED, p.getStatus());
    }

    @Test
    void saleCanceled_marksAnInitiatedPaymentFailed() {
        Payment p = existing(PaymentStatus.INITIATED);

        Outcome outcome = service("prod").handle(signed("sale_canceled", "8000"));

        assertEquals(Outcome.CANCELED, outcome);
        assertEquals(PaymentStatus.FAILED, p.getStatus());
        verify(confirmation, never()).confirmPaid(any());
    }

    @Test
    void saleCanceled_neverDowngradesAPaidPayment() {
        Payment p = existing(PaymentStatus.SUCCEEDED);

        service("prod").handle(signed("sale_canceled", "8000"));

        assertEquals(PaymentStatus.SUCCEEDED, p.getStatus());
    }

    @Test
    void unknownReference_isIgnored() {
        when(payments.findForUpdateByProviderAndProviderRef("PAYTECH", REF)).thenReturn(Optional.empty());

        assertEquals(Outcome.IGNORED, service("prod").handle(signed("sale_complete", "8000")));
        verify(confirmation, never()).confirmPaid(any());
    }

    @Test
    void unknownEvent_isIgnored() {
        Payment p = existing(PaymentStatus.INITIATED);

        assertEquals(Outcome.IGNORED, service("prod").handle(signed("refund_complete", "8000")));
        assertEquals(PaymentStatus.INITIATED, p.getStatus());
    }

    @Test
    void amountMatches_comparesNumerically() {
        Payment p = existing(PaymentStatus.INITIATED);

        assertTrue(PaymentIpnService.amountMatches(p, Map.of("item_price", "8000")));
        assertTrue(PaymentIpnService.amountMatches(p, Map.of("item_price", "9000", "final_item_price", "8000.00")));
        assertFalse(PaymentIpnService.amountMatches(p, Map.of("item_price", "7999")));
    }

    @Test
    void amountMatches_rejectsAWrongCurrencyOrGarbage() {
        Payment p = existing(PaymentStatus.INITIATED);

        assertFalse(PaymentIpnService.amountMatches(p, Map.of("item_price", "8000", "currency", "EUR")));
        assertFalse(PaymentIpnService.amountMatches(p, Map.of("item_price", "8 000")));
        assertFalse(PaymentIpnService.amountMatches(p, Map.of("currency", "XOF")));
    }
}