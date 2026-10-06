package com.footbooking.payment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Point d'entree du domaine reservation : "ce paiement doit etre rembourse". Ne depend d'aucun autre service. */
@Service
public class PaymentRefundRequests {

    private final PaymentRepository payments;

    public PaymentRefundRequests(PaymentRepository payments) {
        this.payments = payments;
    }

    /** Marque les paiements reussis de la reservation a rembourser. Renvoie leur nombre (0 : rien a rembourser). */
    @Transactional
    public int requestRefundFor(UUID bookingId) {
        List<Payment> paid = payments.findByBookingIdAndStatus(bookingId, PaymentStatus.SUCCEEDED);
        for (Payment p : paid) {
            p.setStatus(PaymentStatus.REFUND_PENDING);
            p.setRefundAttempts(0);
        }
        return paid.size();
    }
}