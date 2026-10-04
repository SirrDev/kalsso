package com.footbooking.venue;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;

public final class VenueDtos {
    private VenueDtos() {}

    public record VenueRequest(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 2000) String description,
            @NotBlank @Size(max = 255) String address,
            @NotBlank @Size(max = 100) String city,
            @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Numero de telephone invalide") String phone,
            @Size(max = 50) String timezone) {}

    public record VenueResponse(UUID id, String name, String description, String address, String city,
                                Double latitude, Double longitude, String phone, String timezone,
                                VenueStatus status, Instant createdAt) {
        static VenueResponse from(Venue v) {
            return new VenueResponse(v.getId(), v.getName(), v.getDescription(), v.getAddress(), v.getCity(),
                    v.getLatitude(), v.getLongitude(), v.getPhone(), v.getTimezone(),
                    v.getStatus(), v.getCreatedAt());
        }
    }
}
