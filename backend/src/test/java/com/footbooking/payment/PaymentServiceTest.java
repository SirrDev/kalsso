package com.footbooking.payment;

import com.footbooking.booking.Booking;
import com.footbooking.booking.BookingRepository;
import com.footbooking.booking.BookingStatus;
import com.footbooking.common.ApiException;
import com.footbooking.payment.PaymentDtos.PaymentResponse;
import com.footbooking.payment.PaymentProvider.CheckoutRequest;
import com.footbooking.payment.PaymentProvider.CheckoutSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");

    private final PaymentRepository payments = Mockito.mock(PaymentRepository.class);
    private final BookingRepository bookings = Mockito.mock(BookingRepository.class);
    private final PaymentProvider provider = Mockito.mock(PaymentProvider.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final PaymentService service =
            new PaymentService(payments, bookings, provider, clock, new BigDecimal("3"), 15);

    private final UUID userId = UUID.randomUUID();
    private final UUID bookingId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(provider.name()).thenReturn("PAYTECH");
        when(provider.createCheckout(any())).thenReturn(
                new CheckoutSession("tok", "https://paytech.sn/payment/checkout/tok"));
        when(payments.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Booking booking(BookingStatus status, Instant expiresAt, String price) {
        Booking b = new Booking();
        ReflectionTestUtils.setField(b, "id", bookingId);
        b.setFieldId(UUID.randomUUID());
        b.setUserId(userId);
        b.setStatus(status);
        b.setExpiresAt(expiresAt);
        b.setTotalPrice(new BigDecimal(price));
        b.setCurrency("XOF");
        return b;
    }

    private Booking owned(Booking b) {
        when(bookings.findByIdAndUserId(bookingId, userId)).thenReturn(Optional.of(b));
        return b;
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "attendu " + expected + " mais obtenu " + actual);
    }

    private ApiException failure() {
        return assertThrows(ApiException.class, () -> service.initiate(userId, bookingId));
    }

    @Test
    void initiate_valid_createsAnInitiatedPaymentWithCommission() {
        owned(booking(BookingStatus.PENDING, NOW.plusSeconds(300), "8000"));

        PaymentResponse res = service.initiate(userId, bookingId);

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(payments).save(saved.capture());
        Payment p = saved.getValue();
        assertEquals("PAYTECH", p.getProvider());
        assertEquals(PaymentStatus.INITIATED, p.getStatus());
        assertEquals(32, p.getProviderRef().length());
        assertEquals("tok", p.getProviderToken());
        assertAmount("8000", p.getAmount());
        assertAmount("3", p.getCommissionPercent());
        assertAmount("240", p.getCommissionAmount());
        assertAmount("7760", p.getNetAmount());
        assertEquals("https://paytech.sn/payment/checkout/tok", res.checkoutUrl());

        ArgumentCaptor<CheckoutRequest> sent = ArgumentCaptor.forClass(CheckoutRequest.class);
        verify(provider).createCheckout(sent.capture());
        assertEquals(p.getProviderRef(), sent.getValue().reference());
        assertAmount("8000", sent.getValue().amount());
        assertEquals("XOF", sent.getValue().currency());
    }

    @Test
    void initiate_valid_extendsTheHoldToThePaymentWindow() {
        Booking b = owned(booking(BookingStatus.PENDING, NOW.plusSeconds(300), "8000"));

        PaymentResponse res = service.initiate(userId, bookingId);

        assertEquals(NOW.plusSeconds(15 * 60), b.getExpiresAt());
        assertEquals(NOW.plusSeconds(15 * 60), res.holdExpiresAt());
    }

    @Test
    void initiate_existingCheckout_isReusedWithoutCallingTheProvider() {
        owned(booking(BookingStatus.PENDING, NOW.plusSeconds(300), "8000"));
        Payment existing = new Payment();
        existing.setAmount(new BigDecimal("8000"));
        existing.setCurrency("XOF");
        existing.setCheckoutUrl("https://paytech.sn/payment/checkout/old");
        when(payments.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(any(), eq(PaymentStatus.INITIATED)))
                .thenReturn(Optional.of(existing));

        PaymentResponse res = service.initiate(userId, bookingId);

        assertEquals("https://paytech.sn/payment/checkout/old", res.checkoutUrl());
        verify(provider, never()).createCheckout(any());
        verify(payments, never()).save(any());
    }

    @Test
    void initiate_foreignBooking_returns404() {
        when(bookings.findByIdAndUserId(bookingId, userId)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, failure().getStatus());
        verify(provider, never()).createCheckout(any());
    }

    @Test
    void initiate_confirmedBooking_returns409() {
        owned(booking(BookingStatus.CONFIRMED, null, "8000"));

        assertEquals(HttpStatus.CONFLICT, failure().getStatus());
        verify(provider, never()).createCheckout(any());
    }

    @Test
    void initiate_expiredHold_returns409() {
        owned(booking(BookingStatus.PENDING, NOW.minusSeconds(1), "8000"));

        assertEquals(HttpStatus.CONFLICT, failure().getStatus());
        verify(provider, never()).createCheckout(any());
    }

    @Test
    void initiate_freeBooking_returns409() {
        owned(booking(BookingStatus.PENDING, NOW.plusSeconds(300), "0"));

        assertEquals(HttpStatus.CONFLICT, failure().getStatus());
        verify(provider, never()).createCheckout(any());
    }

    @Test
    void initiate_providerFailure_savesNothing() {
        owned(booking(BookingStatus.PENDING, NOW.plusSeconds(300), "8000"));
        when(provider.createCheckout(any())).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "indisponible"));

        assertEquals(HttpStatus.BAD_GATEWAY, failure().getStatus());
        verify(payments, never()).save(any());
    }

    @Test
    void newHoldExpiry_neverShortensALongerHold() {
        Instant current = NOW.plusSeconds(30 * 60);

        assertEquals(current, PaymentService.newHoldExpiry(current, NOW, 15));
    }

    @Test
    void newHoldExpiry_extendsAShorterHold() {
        assertEquals(NOW.plusSeconds(15 * 60), PaymentService.newHoldExpiry(NOW.plusSeconds(60), NOW, 15));
    }

    @Test
    void newReference_is32HexCharactersAndUnique() {
        String a = PaymentService.newReference();

        assertTrue(a.matches("[0-9a-f]{32}"));
        assertNotEquals(a, PaymentService.newReference());
    }
}