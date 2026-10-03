package com.footbooking.field;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class FieldDtos {
    private FieldDtos() {}

    public record FieldRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull FieldFormat format,
            @NotNull FieldSurface surface,
            @NotNull Integer slotMinutes) {}

    public record FieldResponse(UUID id, UUID venueId, String name, FieldFormat format,
                                FieldSurface surface, int slotMinutes, boolean active) {
        static FieldResponse from(Field f) {
            return new FieldResponse(f.getId(), f.getVenueId(), f.getName(), f.getFormat(),
                    f.getSurface(), f.getSlotMinutes(), f.isActive());
        }
    }
}
