package com.footbooking.availability;

import com.footbooking.availability.AvailabilityDtos.AvailabilityResponse;
import com.footbooking.availability.AvailabilityDtos.SlotResponse;
import com.footbooking.availability.SlotGenerator.Range;
import com.footbooking.availability.SlotGenerator.Slot;
import com.footbooking.common.ApiException;
import com.footbooking.field.Field;
import com.footbooking.field.FieldService;
import com.footbooking.field.OpeningHoursService;
import com.footbooking.field.PricingService;
import com.footbooking.field.PricingService.Price;
import com.footbooking.venue.Venue;
import com.footbooking.venue.VenueService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.footbooking.venue.VenueStatus;
import java.math.BigDecimal;
import java.time.Instant;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

@Service
public class AvailabilityService {

    static final int MAX_DAYS_AHEAD = 90;

    private final FieldService fieldService;
    private final VenueService venueService;
    private final OpeningHoursService hoursService;
    private final PricingService pricingService;
    private final Clock clock;

    public AvailabilityService(FieldService fieldService, VenueService venueService,
                               OpeningHoursService hoursService, PricingService pricingService,
                               Clock clock) {
        this.fieldService = fieldService;
        this.venueService = venueService;
        this.hoursService = hoursService;
        this.pricingService = pricingService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AvailabilityResponse forDay(UUID fieldId, LocalDate date) {
        Field field = fieldService.getActive(fieldId);
        Venue venue = venueService.getById(field.getVenueId());
        if (venue.getStatus() != VenueStatus.ACTIVE) {
            // 404 et non 403 : on ne revele pas l'existence d'un complexe non publie
            throw new ApiException(HttpStatus.NOT_FOUND, "Terrain introuvable");
        }
        ZoneId zone = ZoneId.of(venue.getTimezone());

        // "Aujourd'hui" se lit dans le fuseau du complexe, pas dans celui du serveur
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Les reservations sont ouvertes jusqu'a " + MAX_DAYS_AHEAD + " jours a l'avance");
        }

        List<Range> ranges = hoursService.rangesFor(fieldId, date).stream()
                .map(r -> new Range(r.open(), r.close()))
                .toList();

        List<Slot> slots = SlotGenerator.generate(date, ranges, field.getSlotMinutes(), zone, clock.instant());

        Function<LocalDateTime, Optional<Price>> priceOf = pricingService.priceResolver(fieldId);
        List<SlotResponse> response = slots.stream().map(s -> toResponse(s, date, priceOf)).toList();

        return new AvailabilityResponse(fieldId, date, venue.getTimezone(), field.getSlotMinutes(), response);
    }

    public record BookableSlot(UUID fieldId, Instant start, Instant end, BigDecimal price, String currency) {}

    /**
     * Verifie qu'un creneau est reservable et renvoie ses bornes et son prix.
     * Ne regarde PAS les reservations existantes : c'est la contrainte de la base
     * (booking_no_overlap) qui garantit l'absence de double reservation.
     */
    @Transactional(readOnly = true)
    public BookableSlot requireBookable(UUID fieldId, Instant start) {
        Field field = fieldService.getActive(fieldId);
        Venue venue = venueService.getById(field.getVenueId());
        if (venue.getStatus() != VenueStatus.ACTIVE) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Terrain introuvable");
        }
        ZoneId zone = ZoneId.of(venue.getTimezone());
        LocalDate date = start.atZone(zone).toLocalDate();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Les reservations sont ouvertes jusqu'a " + MAX_DAYS_AHEAD + " jours a l'avance");
        }

        List<Range> ranges = hoursService.rangesFor(fieldId, date).stream()
                .map(r -> new Range(r.open(), r.close()))
                .toList();

        Slot slot = SlotGenerator.generate(date, ranges, field.getSlotMinutes(), zone, clock.instant())
                .stream()
                .filter(s -> s.start().equals(start))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "Ce creneau n'existe pas pour ce terrain"));

        if (slot.status() == SlotGenerator.Status.PAST) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Ce creneau est deja passe");
        }

        Price price = pricingService.priceResolver(fieldId)
                .apply(LocalDateTime.of(date, slot.localStart()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Aucun tarif defini pour ce creneau"));

        return new BookableSlot(fieldId, slot.start(), slot.end(), price.amount(), price.currency());
    }

    private SlotResponse toResponse(Slot s, LocalDate date, Function<LocalDateTime, Optional<Price>> priceOf) {
        Optional<Price> price = priceOf.apply(LocalDateTime.of(date, s.localStart()));
        String status = s.status() == SlotGenerator.Status.PAST ? "PAST"
                : price.isEmpty() ? "NO_PRICE"
                  : "AVAILABLE";
        return new SlotResponse(s.start(), s.end(), s.localStart(), s.localEnd(), status,
                price.map(Price::amount).orElse(null),
                price.map(Price::currency).orElse(null));
    }
}