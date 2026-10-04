package com.footbooking.availability;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class AvailabilityDtos {
    private AvailabilityDtos() {}

    /**
     * status : AVAILABLE (reservable), PAST (deja commence) ou NO_PRICE (aucun tarif defini).
     * BOOKED sera ajoute avec les reservations (B4).
     */
    public record SlotResponse(Instant start, Instant end, LocalTime localStart, LocalTime localEnd,
                               String status, BigDecimal price, String currency) {}

    public record AvailabilityResponse(UUID fieldId, LocalDate date, String timezone,
                                       int slotMinutes, List<SlotResponse> slots) {}
}