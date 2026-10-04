package com.footbooking.booking;

import com.footbooking.booking.OwnerBookingDtos.BlockRequest;
import com.footbooking.booking.OwnerBookingDtos.OwnerBookingResponse;
import com.footbooking.common.ApiException;
import com.footbooking.field.Field;
import com.footbooking.field.FieldService;
import com.footbooking.venue.VenueService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class OwnerBookingService {

    static final int DEFAULT_RANGE_DAYS = 30;
    static final int MAX_RANGE_DAYS = 62;
    static final long MAX_BLOCK_DAYS = 14;

    public record DateRange(LocalDate from, LocalDate to) {}

    private final BookingRepository bookings;
    private final FieldService fieldService;
    private final VenueService venueService;
    private final Clock clock;

    public OwnerBookingService(BookingRepository bookings, FieldService fieldService,
                               VenueService venueService, Clock clock) {
        this.bookings = bookings;
        this.fieldService = fieldService;
        this.venueService = venueService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<OwnerBookingResponse> list(UUID ownerId, UUID fieldId, LocalDate from, LocalDate to) {
        Field field = fieldService.getOwned(ownerId, fieldId);
        ZoneId zone = ZoneId.of(venueService.getById(field.getVenueId()).getTimezone());

        DateRange range = resolveRange(from, to, LocalDate.now(clock.withZone(zone)));
        Instant start = range.from().atStartOfDay(zone).toInstant();
        Instant end = range.to().plusDays(1).atStartOfDay(zone).toInstant();

        return bookings.findForOwner(fieldId, start, end, clock.instant()).stream()
                .map(OwnerBookingResponse::from).toList();
    }

    @Transactional
    public OwnerBookingResponse block(UUID ownerId, UUID fieldId, BlockRequest req) {
        fieldService.getOwned(ownerId, fieldId);
        Instant now = clock.instant();
        validateBlock(req.start(), req.end(), now);

        // Un blocage expire occupe encore la plage tant qu'il est PENDING : on le libere d'abord
        bookings.cancelExpiredHolds(fieldId, req.start(), req.end(), now);

        Booking b = new Booking();
        b.setFieldId(fieldId);
        b.setUserId(null);
        b.setStartTime(req.start());
        b.setEndTime(req.end());
        b.setStatus(BookingStatus.CONFIRMED);
        b.setSource(BookingSource.BLOCK);
        b.setTotalPrice(BigDecimal.ZERO);
        b.setNote(req.note());

        try {
            bookings.saveAndFlush(b);
        } catch (DataIntegrityViolationException e) {
            if (BookingService.isOverlap(e)) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Cette plage chevauche une reservation existante : annulez-la d'abord");
            }
            throw e;
        }
        return OwnerBookingResponse.from(b);
    }

    @Transactional
    public void unblock(UUID ownerId, UUID fieldId, UUID bookingId) {
        fieldService.getOwned(ownerId, fieldId);
        Booking b = bookings.findById(bookingId)
                .filter(x -> x.getFieldId().equals(fieldId))
                .filter(x -> x.getSource() == BookingSource.BLOCK)
                .filter(x -> x.getStatus() == BookingStatus.CONFIRMED)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Blocage introuvable"));
        b.setStatus(BookingStatus.CANCELLED);
    }

    // ---------- Regles pures (testables sans base de donnees) ----------

    static DateRange resolveRange(LocalDate from, LocalDate to, LocalDate today) {
        LocalDate start = from != null ? from : today;
        LocalDate end = to != null ? to : start.plusDays(DEFAULT_RANGE_DAYS);
        if (end.isBefore(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La date de fin precede la date de debut");
        }
        if (start.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "La periode ne peut pas depasser " + MAX_RANGE_DAYS + " jours");
        }
        return new DateRange(start, end);
    }

    static void validateBlock(Instant start, Instant end, Instant now) {
        if (!end.isAfter(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La fin doit etre apres le debut");
        }
        if (Duration.between(start, end).compareTo(Duration.ofDays(MAX_BLOCK_DAYS)) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Un blocage ne peut pas depasser " + MAX_BLOCK_DAYS + " jours");
        }
        if (!end.isAfter(now)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cette plage est deja passee");
        }
    }
}