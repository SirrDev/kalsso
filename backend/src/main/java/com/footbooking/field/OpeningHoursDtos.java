package com.footbooking.field;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class OpeningHoursDtos {
    private OpeningHoursDtos() {}

    public record OpeningSlot(
            @NotNull @Min(1) @Max(7) Integer dayOfWeek,   // 1 = lundi ... 7 = dimanche
            @NotNull LocalTime openTime,
            @NotNull LocalTime closeTime) {}

    public record OpeningHoursRequest(
            @NotNull @Size(max = 50) List<@Valid OpeningSlot> slots) {}

    public record OpeningSlotResponse(UUID id, int dayOfWeek, LocalTime openTime, LocalTime closeTime) {
        static OpeningSlotResponse from(OpeningHours h) {
            return new OpeningSlotResponse(h.getId(), h.getDayOfWeek(), h.getOpenTime(), h.getCloseTime());
        }
    }

    public record ClosedDayRequest(
            @NotNull LocalDate day,
            @Size(max = 255) String reason) {}

    public record ClosedDayResponse(UUID id, LocalDate day, String reason) {
        static ClosedDayResponse from(ClosedDay c) {
            return new ClosedDayResponse(c.getId(), c.getDay(), c.getReason());
        }
    }
}