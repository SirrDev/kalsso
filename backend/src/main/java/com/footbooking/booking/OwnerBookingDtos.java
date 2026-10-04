package com.footbooking.booking;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class OwnerBookingDtos {
    private OwnerBookingDtos() {}

    public record BlockRequest(@NotNull Instant start, @NotNull Instant end, @Size(max = 255) String note) {}

    public record OwnerBookingResponse(UUID id, UUID fieldId, Instant start, Instant end,
                                       BookingStatus status, BookingSource source, UUID userId,
                                       BigDecimal totalPrice, String currency, String note) {
        static OwnerBookingResponse from(Booking b) {
            return new OwnerBookingResponse(b.getId(), b.getFieldId(), b.getStartTime(), b.getEndTime(),
                    b.getStatus(), b.getSource(), b.getUserId(), b.getTotalPrice(), b.getCurrency(), b.getNote());
        }
    }
}