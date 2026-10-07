package com.footbooking.booking;

import com.footbooking.common.ApiException;
import com.footbooking.notification.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingConfirmation {

    public enum Result { CONFIRMED, ALREADY_FINAL }

    private final BookingRepository bookings;
    private final NotificationService notifications;
    private final Clock clock;

    public BookingConfirmation(BookingRepository bookings, NotificationService notifications, Clock clock) {
        this.bookings = bookings;
        this.notifications = notifications;
        this.clock = clock;
    }

    /**
     * Transaction independante : si la base refuse (creneau repris par un autre), seule cette tentative
     * est annulee et DataIntegrityViolationException remonte a l'appelant.
     * La notification est ecrite dans cette meme transaction : tout passe ou rien ne passe.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result confirmPaid(UUID bookingId) {
        Booking b = bookings.findById(bookingId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Reservation introuvable"));
        // Les blocages expires d'autres clients occupent encore le creneau tant qu'ils sont PENDING
        bookings.cancelExpiredHolds(b.getFieldId(), b.getStartTime(), b.getEndTime(), clock.instant());
        int updated = bookings.confirmPaidBooking(bookingId);
        if (updated == 0) {
            return Result.ALREADY_FINAL;
        }
        // Un blocage proprietaire n'a pas de client : rien a notifier
        if (b.getUserId() != null) {
            notifications.enqueue(b.getUserId(), "booking_confirmed",
                    Map.of("start", b.getStartTime().toString(),
                            "end", b.getEndTime().toString(),
                            "price", b.getTotalPrice().toPlainString(),
                            "currency", b.getCurrency()),
                    "booking-" + bookingId + "-confirmed");
        }
        return Result.CONFIRMED;
    }
}