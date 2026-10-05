package com.footbooking.payment;

import com.footbooking.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Developpement local uniquement. Actif seulement si app.payment.provider=fake (jamais par defaut). */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "fake")
public class FakePaymentProvider implements PaymentProvider {

    private final String refundMode;

    /** refundMode : accepted (defaut), rejected ou unavailable. */
    public FakePaymentProvider(@Value("${app.payment.fake-refund-mode:accepted}") String refundMode) {
        this.refundMode = refundMode;
    }

    @Override
    public String name() {
        return "FAKE";
    }

    @Override
    public CheckoutSession createCheckout(CheckoutRequest request) {
        return new CheckoutSession("fake-" + request.reference(),
                "https://fake-pay.local/checkout/" + request.reference());
    }

    @Override
    public RefundResult refund(String reference) {
        return switch (refundMode) {
            case "rejected" -> new RefundResult(false, "refus simule");
            case "unavailable" -> throw new ApiException(HttpStatus.BAD_GATEWAY, "indisponible (simulation)");
            default -> new RefundResult(true, "ok");
        };
    }
}