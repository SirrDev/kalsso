package com.footbooking.payment;

import com.footbooking.booking.Booking;
import com.footbooking.booking.BookingRepository;
import com.footbooking.booking.BookingStatus;
import com.footbooking.common.ApiException;
import com.footbooking.payment.PaymentDtos.PaymentResponse;
import com.footbooking.payment.PaymentProvider.CheckoutRequest;
import com.footbooking.payment.PaymentProvider.CheckoutSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final PaymentProvider provider;
    private final Clock clock;
    private final BigDecimal commissionPercent;
    private final long holdMinutes;

    public PaymentService(PaymentRepository payments, BookingRepository bookings, PaymentProvider provider,
                          Clock clock,
                          @Value("${app.payment.commission-percent:3}") BigDecimal commissionPercent,
                          @Value("${app.payment.hold-minutes:15}") long holdMinutes) {
        this.payments = payments;
        this.bookings = bookings;
        this.provider = provider;
        this.clock = clock;
        this.commissionPercent = commissionPercent;
        this.holdMinutes = holdMinutes;
    }

    /**
     * Si le fournisseur echoue, l'exception annule la transaction : ni paiement orphelin,
     * ni prolongation du blocage.
     */
    @Transactional
    public PaymentResponse initiate(UUID userId, UUID bookingId) {
        // 404 si la reservation n'est pas a lui : on ne revele pas son existence
        Booking booking = bookings.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Reservation introuvable"));
        Instant now = clock.instant();
        requirePayable(booking, now);

        booking.setExpiresAt(newHoldExpiry(booking.getExpiresAt(), now, holdMinutes));

        // Deuxieme clic : on renvoie le meme paiement, jamais un second
        Optional<Payment> existing = payments
                .findFirstByBookingIdAndStatusOrderByCreatedAtDesc(booking.getId(), PaymentStatus.INITIATED)
                .filter(p -> p.getCheckoutUrl() != null);
        if (existing.isPresent()) {
            return toResponse(existing.get(), booking);
        }

        Commission.Split split = Commission.split(booking.getTotalPrice(), commissionPercent, booking.getCurrency());

        Payment payment = new Payment();
        payment.setBookingId(booking.getId());
        payment.setProvider(provider.name());
        payment.setProviderRef(newReference());
        payment.setAmount(booking.getTotalPrice());
        payment.setCurrency(booking.getCurrency());
        payment.setCommissionPercent(commissionPercent);
        payment.setCommissionAmount(split.commission());
        payment.setNetAmount(split.net());

        CheckoutSession session = provider.createCheckout(new CheckoutRequest(
                payment.getProviderRef(), "Reservation de terrain", booking.getTotalPrice(),
                booking.getCurrency(), "Reservation de terrain Footbooking"));
        payment.setProviderToken(session.token());
        payment.setCheckoutUrl(session.redirectUrl());

        return toResponse(payments.save(payment), booking);
    }

    // ---------- Regles pures (testables sans base de donnees) ----------

    static void requirePayable(Booking b, Instant now) {
        if (b.getStatus() != BookingStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "Cette reservation n'est pas en attente de paiement");
        }
        if (b.getExpiresAt() == null || !b.getExpiresAt().isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "Le blocage de cette reservation a expire");
        }
        if (b.getTotalPrice().signum() <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Cette reservation n'a aucun montant a payer");
        }
    }

    /** Prolonge le blocage jusqu'a now + holdMinutes, sans jamais le raccourcir. */
    static Instant newHoldExpiry(Instant current, Instant now, long holdMinutes) {
        Instant target = now.plus(holdMinutes, ChronoUnit.MINUTES);
        return current.isBefore(target) ? target : current;
    }

    /** 32 caracteres hexadecimaux : notre reference unique, renvoyee par le fournisseur dans sa notification. */
    static String newReference() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private PaymentResponse toResponse(Payment p, Booking b) {
        return new PaymentResponse(p.getId(), b.getId(), p.getAmount(), p.getCurrency(),
                p.getCheckoutUrl(), b.getExpiresAt());
    }
}