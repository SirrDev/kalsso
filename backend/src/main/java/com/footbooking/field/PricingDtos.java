package com.footbooking.field;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

public final class PricingDtos {
    private PricingDtos() {}

    public record PricingRuleRequest(
            @Min(1) @Max(7) Integer dayOfWeek,            // null = tous les jours
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotNull @DecimalMin("0.0") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @Pattern(regexp = "^[A-Z]{3}$", message = "Devise sur 3 lettres majuscules (ex: XOF)") String currency) {}

    public record PricingRuleResponse(UUID id, Integer dayOfWeek, LocalTime startTime,
                                      LocalTime endTime, BigDecimal price, String currency) {
        static PricingRuleResponse from(PricingRule r) {
            return new PricingRuleResponse(r.getId(), r.getDayOfWeek(), r.getStartTime(),
                    r.getEndTime(), r.getPrice(), r.getCurrency());
        }
    }
}