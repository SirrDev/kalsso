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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

    private final BookingRepository bookings;
    private final AvailabilityService availability;
    private final Clock clock;
    private final long holdMinutes;
    private final long cancelHours;

    public BookingService(BookingRepository bookings, AvailabilityService availability, Clock clock,
                          @Value("${app.booking.hold-minutes:10}") long holdMinutes,
                          @Value("${app.booking.cancel-hours:24}") long cancelHours) {
        this.bookings = bookings;
        this.availability = availability;
        this.clock = clock;
        this.holdMinutes = holdMinutes;
        this.cancelHours = cancelHours;
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

    @Transactional(readOnly = true)
    public List<BookingResponse> listMine(UUID userId) {
        return bookings.findTop100ByUserIdOrderByStartTimeDesc(userId).stream()
                .map(BookingResponse::from).toList();
    }

    @Transactional
    public BookingResponse cancel(UUID userId, UUID bookingId) {
        // 404 si la reservation n'est pas a lui : on ne revele pas son existence
        Booking b = bookings.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Reservation introuvable"));
        checkCancellable(b, clock.instant(), cancelHours);
        b.setStatus(BookingStatus.CANCELLED);
        b.setExpiresAt(null);
        return BookingResponse.from(b);
    }

    /** Regle d'annulation, isolee pour etre testee sans base de donnees. */
    static void checkCancellable(Booking b, Instant now, long cancelHours) {
        if (b.getStatus() == BookingStatus.CANCELLED) {
            throw new ApiException(HttpStatus.CONFLICT, "Cette reservation est deja annulee");
        }
        if (b.getStatus() != BookingStatus.PENDING && b.getStatus() != BookingStatus.CONFIRMED) {
            throw new ApiException(HttpStatus.CONFLICT, "Cette reservation ne peut plus etre annulee");
        }
        if (b.getStatus() == BookingStatus.CONFIRMED
                && now.isAfter(b.getStartTime().minus(cancelHours, ChronoUnit.HOURS))) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Annulation impossible moins de " + cancelHours + " h avant le debut");
        }
    }

    static boolean isOverlap(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains("booking_no_overlap");
    }
}