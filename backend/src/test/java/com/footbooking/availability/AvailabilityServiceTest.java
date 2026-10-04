package com.footbooking.availability;

import com.footbooking.availability.AvailabilityDtos.AvailabilityResponse;
import com.footbooking.common.ApiException;
import com.footbooking.field.Field;
import com.footbooking.field.FieldService;
import com.footbooking.field.OpeningHoursService;
import com.footbooking.field.OpeningHoursService.DayRange;
import com.footbooking.field.PricingService;
import com.footbooking.field.PricingService.Price;
import com.footbooking.venue.Venue;
import com.footbooking.venue.VenueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import com.footbooking.venue.VenueStatus;
import com.footbooking.booking.BookingOccupancy;
import com.footbooking.booking.BookingOccupancy.Occupied;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class AvailabilityServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 10);

    private final FieldService fieldService = Mockito.mock(FieldService.class);
    private final VenueService venueService = Mockito.mock(VenueService.class);
    private final OpeningHoursService hoursService = Mockito.mock(OpeningHoursService.class);
    private final PricingService pricingService = Mockito.mock(PricingService.class);
    private final BookingOccupancy bookingOccupancy = Mockito.mock(BookingOccupancy.class);

    private final UUID fieldId = UUID.randomUUID();
    private final UUID venueId = UUID.randomUUID();
    private final Venue venue = new Venue();
    private final Field field = new Field();

    @BeforeEach
    void setUp() {
        venue.setTimezone("Africa/Dakar");
        venue.setStatus(VenueStatus.ACTIVE);
        field.setVenueId(venueId);
        field.setSlotMinutes(60);
        when(fieldService.getActive(fieldId)).thenReturn(field);
        when(venueService.getById(venueId)).thenReturn(venue);
        // par defaut : 5000 XOF a toute heure
        when(pricingService.priceResolver(fieldId))
                .thenReturn(dt -> Optional.of(new Price(new BigDecimal("5000"), "XOF")));
    }

    private AvailabilityService serviceAt(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new AvailabilityService(fieldService, venueService, hoursService, pricingService,
                bookingOccupancy, clock);
    }

    private static List<DayRange> hours(String open, String close) {
        return List.of(new DayRange(LocalTime.parse(open), LocalTime.parse(close)));
    }

    @Test
    void openDay_returnsPricedAvailableSlots() {
        when(hoursService.rangesFor(fieldId, DAY)).thenReturn(hours("18:00", "20:00"));

        AvailabilityResponse res = serviceAt("2026-10-01T12:00:00Z").forDay(fieldId, DAY);

        assertEquals(2, res.slots().size());
        assertEquals("AVAILABLE", res.slots().get(0).status());
        assertEquals(new BigDecimal("5000"), res.slots().get(0).price());
        assertEquals("XOF", res.slots().get(0).currency());
        assertEquals("Africa/Dakar", res.timezone());
    }

    @Test
    void closedDay_returnsNoSlots() {
        when(hoursService.rangesFor(fieldId, DAY)).thenReturn(List.of());

        AvailabilityResponse res = serviceAt("2026-10-01T12:00:00Z").forDay(fieldId, DAY);

        assertTrue(res.slots().isEmpty());
    }

    @Test
    void pendingVenue_isHiddenFromThePublic() {
        venue.setStatus(VenueStatus.PENDING);

        ApiException ex = assertThrows(ApiException.class,
                () -> serviceAt("2026-10-01T12:00:00Z").forDay(fieldId, DAY));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void suspendedVenue_isHiddenFromThePublic() {
        venue.setStatus(VenueStatus.SUSPENDED);

        ApiException ex = assertThrows(ApiException.class,
                () -> serviceAt("2026-10-01T12:00:00Z").forDay(fieldId, DAY));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void slotWithoutTariff_isFlaggedNoPrice() {
        when(hoursService.rangesFor(fieldId, DAY)).thenReturn(hours("18:00", "20:00"));
        when(pricingService.priceResolver(fieldId)).thenReturn(dt ->
                dt.toLocalTime().equals(LocalTime.of(18, 0))
                        ? Optional.of(new Price(new BigDecimal("5000"), "XOF"))
                        : Optional.empty());

        AvailabilityResponse res = serviceAt("2026-10-01T12:00:00Z").forDay(fieldId, DAY);

        assertEquals("AVAILABLE", res.slots().get(0).status());
        assertEquals("NO_PRICE", res.slots().get(1).status());
        assertNull(res.slots().get(1).price());
    }

    @Test
    void slotsAlreadyStarted_arePast() {
        when(hoursService.rangesFor(fieldId, DAY)).thenReturn(hours("18:00", "20:00"));

        // 18h30 a Dakar (UTC+0) : le creneau de 18h est passe, celui de 19h non
        AvailabilityResponse res = serviceAt("2026-10-10T18:30:00Z").forDay(fieldId, DAY);

        assertEquals("PAST", res.slots().get(0).status());
        assertEquals("AVAILABLE", res.slots().get(1).status());
    }

    @Test
    void venueTimezoneIsUsedToBuildInstants() {
        venue.setTimezone("Europe/Paris");   // UTC+2 le 10 octobre
        when(hoursService.rangesFor(fieldId, DAY)).thenReturn(hours("18:00", "19:00"));

        AvailabilityResponse res = serviceAt("2026-10-01T12:00:00Z").forDay(fieldId, DAY);

        assertEquals(Instant.parse("2026-10-10T16:00:00Z"), res.slots().get(0).start());
    }

    @Test
    void dateTooFarAhead_returns400() {
        LocalDate tooFar = LocalDate.of(2026, 10, 1).plusDays(AvailabilityService.MAX_DAYS_AHEAD + 1);

        ApiException ex = assertThrows(ApiException.class,
                () -> serviceAt("2026-10-01T12:00:00Z").forDay(fieldId, tooFar));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void requireBookable_validSlot_returnsBoundsAndPrice() {
        when(hoursService.rangesFor(fieldId, DAY)).thenReturn(hours("18:00", "20:00"));

        AvailabilityService.BookableSlot s = serviceAt("2026-10-01T12:00:00Z")
                .requireBookable(fieldId, Instant.parse("2026-10-10T19:00:00Z"));

        assertEquals(Instant.parse("2026-10-10T20:00:00Z"), s.end());
        assertEquals(new BigDecimal("5000"), s.price());
        assertEquals("XOF", s.currency());
    }

    @Test
    void requireBookable_startNotOnASlotBoundary_returns400() {
        when(hoursService.rangesFor(fieldId, DAY)).thenReturn(hours("18:00", "20:00"));

        ApiException ex = assertThrows(ApiException.class, () -> serviceAt("2026-10-01T12:00:00Z")
                .requireBookable(fieldId, Instant.parse("2026-10-10T19:30:00Z")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void requireBookable_pastSlot_returns400() {
        when(hoursService.rangesFor(fieldId, DAY)).thenReturn(hours("18:00", "20:00"));

        // 19h30 : le creneau de 19h a deja commence
        ApiException ex = assertThrows(ApiException.class, () -> serviceAt("2026-10-10T19:30:00Z")
                .requireBookable(fieldId, Instant.parse("2026-10-10T19:00:00Z")));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void requireBookable_noTariff_returns422() {
        when(hoursService.rangesFor(fieldId, DAY)).thenReturn(hours("18:00", "20:00"));
        when(pricingService.priceResolver(fieldId)).thenReturn(dt -> Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> serviceAt("2026-10-01T12:00:00Z")
                .requireBookable(fieldId, Instant.parse("2026-10-10T18:00:00Z")));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
    }

    @Test
    void requireBookable_pendingVenue_returns404() {
        venue.setStatus(VenueStatus.PENDING);

        ApiException ex = assertThrows(ApiException.class, () -> serviceAt("2026-10-01T12:00:00Z")
                .requireBookable(fieldId, Instant.parse("2026-10-10T18:00:00Z")));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void bookedSlot_isFlaggedBooked_andNeighbourStaysAvailable() {
        when(hoursService.rangesFor(fieldId, DAY)).thenReturn(hours("18:00", "20:00"));
        when(bookingOccupancy.forField(eq(fieldId), any(), any())).thenReturn(List.of(
                new Occupied(Instant.parse("2026-10-10T18:00:00Z"), Instant.parse("2026-10-10T19:00:00Z"))));

        AvailabilityResponse res = serviceAt("2026-10-01T12:00:00Z").forDay(fieldId, DAY);

        assertEquals("BOOKED", res.slots().get(0).status());
        assertEquals("AVAILABLE", res.slots().get(1).status());
    }

    @Test
    void dayBounds_areComputedInTheVenueTimezone() {
        venue.setTimezone("Europe/Paris");
        when(hoursService.rangesFor(fieldId, DAY)).thenReturn(List.of());

        serviceAt("2026-10-01T12:00:00Z").forDay(fieldId, DAY);

        verify(bookingOccupancy).forField(fieldId,
                Instant.parse("2026-10-09T22:00:00Z"), Instant.parse("2026-10-10T22:00:00Z"));
    }

    @Test
    void isOccupied_detectsAnyOverlap() {
        SlotGenerator.Slot slot = new SlotGenerator.Slot(
                Instant.parse("2026-10-10T19:00:00Z"), Instant.parse("2026-10-10T20:00:00Z"),
                LocalTime.of(19, 0), LocalTime.of(20, 0), SlotGenerator.Status.AVAILABLE);

        assertTrue(AvailabilityService.isOccupied(slot, List.of(
                new Occupied(Instant.parse("2026-10-10T19:30:00Z"), Instant.parse("2026-10-10T20:30:00Z")))));
    }

    @Test
    void isOccupied_touchingBookingsDoNotBlock() {
        SlotGenerator.Slot slot = new SlotGenerator.Slot(
                Instant.parse("2026-10-10T19:00:00Z"), Instant.parse("2026-10-10T20:00:00Z"),
                LocalTime.of(19, 0), LocalTime.of(20, 0), SlotGenerator.Status.AVAILABLE);

        assertFalse(AvailabilityService.isOccupied(slot, List.of(
                new Occupied(Instant.parse("2026-10-10T18:00:00Z"), Instant.parse("2026-10-10T19:00:00Z")),
                new Occupied(Instant.parse("2026-10-10T20:00:00Z"), Instant.parse("2026-10-10T21:00:00Z")))));
    }
}