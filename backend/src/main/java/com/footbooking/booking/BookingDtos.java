package com.footbooking.booking;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class BookingDtos {
    private BookingDtos() {}

    public record CreateBookingRequest(@NotNull UUID fieldId, @NotNull Instant start) {}

    public record BookingResponse(UUID id, UUID fieldId, Instant start, Instant end, BookingStatus status,
                                  BigDecimal totalPrice, String currency, Instant expiresAt) {
        static BookingResponse from(Booking b) {
            return new BookingResponse(b.getId(), b.getFieldId(), b.getStartTime(), b.getEndTime(),
                    b.getStatus(), b.getTotalPrice(), b.getCurrency(), b.getExpiresAt());
        }
    }
}