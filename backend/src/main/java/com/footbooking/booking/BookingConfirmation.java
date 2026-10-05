package com.footbooking.booking;

import com.footbooking.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class BookingConfirmation {

    public enum Result { CONFIRMED, ALREADY_FINAL }

    private final BookingRepository bookings;
    private final Clock clock;

    public BookingConfirmation(BookingRepository bookings, Clock clock) {
        this.bookings = bookings;
        this.clock = clock;
    }

    /**
     * Transaction independante : si la base refuse (creneau repris par un autre), seule cette tentative
     * est annulee et DataIntegrityViolationException remonte a l'appelant.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result confirmPaid(UUID bookingId) {
        Booking b = bookings.findById(bookingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Reservation introuvable"));
        // Les blocages expires d'autres clients occupent encore le creneau tant qu'ils sont PENDING
        bookings.cancelExpiredHolds(b.getFieldId(), b.getStartTime(), b.getEndTime(), clock.instant());
        int updated = bookings.confirmPaidBooking(bookingId);
        return updated > 0 ? Result.CONFIRMED : Result.ALREADY_FINAL;
    }
}