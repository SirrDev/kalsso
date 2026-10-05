package com.footbooking.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Developpement local uniquement. Actif seulement si app.payment.provider=fake (jamais par defaut). */
@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "fake")
public class FakePaymentProvider implements PaymentProvider {

    @Override
    public String name() {
        return "FAKE";
    }

    @Override
    public CheckoutSession createCheckout(CheckoutRequest request) {
        return new CheckoutSession("fake-" + request.reference(),
                "https://fake-pay.local/checkout/" + request.reference());
    }
}