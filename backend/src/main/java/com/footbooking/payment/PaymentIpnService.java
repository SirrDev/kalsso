package com.footbooking.payment;

import com.footbooking.booking.BookingConfirmation;
import com.footbooking.booking.BookingService;
import com.footbooking.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;

@Service
public class PaymentIpnService {

    private static final Logger log = LoggerFactory.getLogger(PaymentIpnService.class);

    public enum Outcome { CONFIRMED, REFUND_PENDING, DUPLICATE, CANCELED, IGNORED }

    private final PaymentRepository payments;
    private final BookingConfirmation confirmation;
    private final PaymentProvider provider;
    private final Clock clock;
    private final String apiKey;
    private final String apiSecret;
    private final boolean strictAmount;

    public PaymentIpnService(PaymentRepository payments, BookingConfirmation confirmation,
                             PaymentProvider provider, Clock clock,
                             @Value("${app.paytech.api-key:}") String apiKey,
                             @Value("${app.paytech.api-secret:}") String apiSecret,
                             @Value("${app.paytech.env:test}") String env) {
        this.payments = payments;
        this.confirmation = confirmation;
        this.provider = provider;
        this.clock = clock;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.strictAmount = "prod".equals(env);
    }

    @Transactional
    public Outcome handle(Map<String, String> params) {
        // 1. Authenticite d'abord : aucune lecture en base tant que la signature n'est pas valide
        if (!PayTechSignature.isAuthentic(params, apiKey, apiSecret)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Signature invalide");
        }
        String reference = params.get("ref_command").trim();   // present : garanti par la verification
        String event = params.getOrDefault("type_event", "").trim();

        // 2. Verrou sur la ligne : deux notifications simultanees sont traitees l'une apres l'autre
        Payment payment = payments.findForUpdateByProviderAndProviderRef(provider.name(), reference).orElse(null);
        if (payment == null) {
            log.error("Notification valide pour une reference inconnue : {}", reference);
            return Outcome.IGNORED;
        }

        Outcome outcome = switch (event) {
            case "sale_complete" -> onPaid(payment, params);
            case "sale_canceled" -> onCanceled(payment);
            default -> Outcome.IGNORED;
        };
        // On ne journalise jamais le contenu de la notification (numero de telephone, signature)
        log.info("Notification {} pour {} : {}", event, reference, outcome);
        return outcome;
    }

    private Outcome onPaid(Payment payment, Map<String, String> params) {
        if (payment.getStatus() == PaymentStatus.SUCCEEDED
                || payment.getStatus() == PaymentStatus.REFUND_PENDING
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            return Outcome.DUPLICATE;
        }
        if (!amountMatches(payment, params)) {
            if (strictAmount) {
                log.error("Montant ou devise inattendu pour {} : paiement NON confirme, verification manuelle requise",
                        payment.getProviderRef());
                return Outcome.IGNORED;
            }
            log.warn("Montant ou devise different pour {} : tolere en environnement de test", payment.getProviderRef());
        }

        // L'argent est recu quoi qu'il arrive a la reservation
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setPaidAt(clock.instant());
        try {
            confirmation.confirmPaid(payment.getBookingId());
            return Outcome.CONFIRMED;
        } catch (DataIntegrityViolationException e) {
            if (!BookingService.isOverlap(e)) {
                throw e;
            }
            // Paiement tardif et creneau repris par un autre client : a rembourser
            payment.setStatus(PaymentStatus.REFUND_PENDING);
            log.warn("Creneau deja repris pour le paiement {} : remboursement a effectuer", payment.getProviderRef());
            return Outcome.REFUND_PENDING;
        }
    }

    private Outcome onCanceled(Payment payment) {
        // Une annulation ne retrograde jamais un paiement deja reussi
        if (payment.getStatus() == PaymentStatus.INITIATED) {
            payment.setStatus(PaymentStatus.FAILED);
        }
        return Outcome.CANCELED;
    }

    // ---------- Regle pure (testable sans base de donnees) ----------

    static boolean amountMatches(Payment payment, Map<String, String> params) {
        String reported = firstNonBlank(params.get("final_item_price"), params.get("item_price"));
        if (reported == null) {
            return false;
        }
        try {
            if (new BigDecimal(reported).compareTo(payment.getAmount()) != 0) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        String currency = params.get("currency");
        return currency == null || currency.isBlank() || currency.trim().equalsIgnoreCase(payment.getCurrency().trim());
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        return b != null && !b.isBlank() ? b.trim() : null;
    }
}