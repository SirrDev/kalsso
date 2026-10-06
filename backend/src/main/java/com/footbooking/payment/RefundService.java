package com.footbooking.payment;

import com.footbooking.payment.PaymentProvider.RefundResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);
    static final int BATCH_SIZE = 20;

    public enum Outcome { REFUNDED, RETRY_LATER, GAVE_UP, SKIPPED }

    private final PaymentRepository payments;
    private final PaymentProvider provider;
    private final Clock clock;
    private final int maxAttempts;
    private final long retryMinutes;

    public RefundService(PaymentRepository payments, PaymentProvider provider, Clock clock,
                         @Value("${app.payment.refund.max-attempts:8}") int maxAttempts,
                         @Value("${app.payment.refund.retry-minutes:10}") long retryMinutes) {
        this.payments = payments;
        this.provider = provider;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
        this.retryMinutes = retryMinutes;
    }

    @Transactional(readOnly = true)
    public List<UUID> dueIds() {
        Instant before = clock.instant().minus(retryMinutes, ChronoUnit.MINUTES);
        return payments.findRefundDueIds(maxAttempts, before, PageRequest.of(0, BATCH_SIZE));
    }

    @Transactional(readOnly = true)
    public List<Payment> listPending() {
        return payments.findTop100ByStatusOrderByCreatedAtAsc(PaymentStatus.REFUND_PENDING);
    }

    /**
     * Une tentative de remboursement, dans sa propre transaction (appelee depuis un autre bean).
     * force=true : ignore le delai et la limite de tentatives (reprise manuelle par un admin).
     */
    @Transactional
    public Outcome attempt(UUID paymentId, boolean force) {
        // Verrou : deux tentatives simultanees sur le meme paiement sont traitees l'une apres l'autre
        Payment p = payments.findForUpdateById(paymentId).orElse(null);
        if (p == null || p.getStatus() != PaymentStatus.REFUND_PENDING) {
            return Outcome.SKIPPED;
        }
        Instant now = clock.instant();
        if (!force && !isDue(p, now, maxAttempts, retryMinutes)) {
            return Outcome.SKIPPED;
        }

        p.setRefundAttempts(p.getRefundAttempts() + 1);
        p.setRefundLastAttemptAt(now);

        boolean accepted = false;
        try {
            RefundResult result = provider.refund(p.getProviderRef());
            accepted = result.accepted();
            if (!accepted) {
                log.warn("Remboursement refuse pour {} : {}", p.getProviderRef(), result.message());
            }
        } catch (RuntimeException e) {
            // Toute panne est une tentative comptee : sinon la transaction serait annulee et le compteur perdu
            log.warn("Remboursement de {} impossible pour le moment : {}", p.getProviderRef(), e.getMessage());
        }

        if (accepted) {
            p.setStatus(PaymentStatus.REFUNDED);
            p.setRefundedAt(now);
            log.info("Paiement {} rembourse", p.getProviderRef());
            return Outcome.REFUNDED;
        }
        if (p.getRefundAttempts() >= maxAttempts) {
            log.error("REMBOURSEMENT MANUEL REQUIS : paiement {} apres {} tentatives", p.getProviderRef(), p.getRefundAttempts());
            return Outcome.GAVE_UP;
        }
        return Outcome.RETRY_LATER;
    }

    // ---------- Regle pure (testable sans base de donnees) ----------

    static boolean isDue(Payment p, Instant now, int maxAttempts, long retryMinutes) {
        if (p.getRefundAttempts() >= maxAttempts) {
            return false;
        }
        Instant last = p.getRefundLastAttemptAt();
        return last == null || !last.isAfter(now.minus(retryMinutes, ChronoUnit.MINUTES));
    }
}