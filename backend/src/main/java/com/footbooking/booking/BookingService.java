package com.footbooking.booking;

import com.footbooking.availability.AvailabilityService;
import com.footbooking.availability.AvailabilityService.BookableSlot;
import com.footbooking.booking.BookingDtos.BookingResponse;
import com.footbooking.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class BookingService {

    private final BookingRepository bookings;
    private final AvailabilityService availability;
    private final Clock clock;
    private final long holdMinutes;

    public BookingService(BookingRepository bookings, AvailabilityService availability, Clock clock,
                          @Value("${app.booking.hold-minutes:10}") long holdMinutes) {
        this.bookings = bookings;
        this.availability = availability;
        this.clock = clock;
        this.holdMinutes = holdMinutes;
    }

    /** Cree une reservation PENDING (blocage de quelques minutes en attendant le paiement). */
    @Transactional
    public BookingResponse create(UUID userId, UUID fieldId, Instant start) {
        BookableSlot slot = availability.requireBookable(fieldId, start);
        Instant now = clock.instant();

        // Un blocage expire occupe encore le creneau tant qu'il est PENDING : on le libere d'abord
        bookings.cancelExpiredHolds(fieldId, slot.start(), slot.end(), now);

        Booking b = new Booking();
        b.setFieldId(fieldId);
        b.setUserId(userId);
        b.setStartTime(slot.start());
        b.setEndTime(slot.end());
        b.setStatus(BookingStatus.PENDING);
        b.setSource(BookingSource.ONLINE);
        b.setTotalPrice(slot.price());
        b.setCurrency(slot.currency());
        b.setExpiresAt(now.plus(holdMinutes, ChronoUnit.MINUTES));

        try {
            // saveAndFlush : la contrainte SQL est evaluee ici, pas a la fin de la transaction
            bookings.saveAndFlush(b);
        } catch (DataIntegrityViolationException e) {
            if (isOverlap(e)) {
                throw new ApiException(HttpStatus.CONFLICT, "Ce creneau vient d'etre pris");
            }
            throw e;
        }
        return BookingResponse.from(b);
    }

    /** Appelee par la tache planifiee. Renvoie le nombre de reservations annulees. */
    @Transactional
    public int releaseExpiredHolds() {
        return bookings.cancelAllExpiredHolds(clock.instant());
    }

    static boolean isOverlap(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains("booking_no_overlap");
    }
}