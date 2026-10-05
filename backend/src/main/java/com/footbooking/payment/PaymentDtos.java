package com.footbooking.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record PaymentResponse(UUID paymentId, UUID bookingId, BigDecimal amount, String currency,
                                  String checkoutUrl, Instant holdExpiresAt) {}
}