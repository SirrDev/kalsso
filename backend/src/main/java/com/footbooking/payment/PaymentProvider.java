package com.footbooking.payment;

import java.math.BigDecimal;

/** Abstraction du fournisseur de paiement : permet de changer de fournisseur ou de tester sans reseau. */
public interface PaymentProvider {

    /** Nom stocke dans payment.provider (ex : PAYTECH). */
    String name();

    CheckoutSession createCheckout(CheckoutRequest request);

    /** reference : notre identifiant unique, que le fournisseur nous renverra dans sa notification. */
    record CheckoutRequest(String reference, String itemName, BigDecimal amount,
                           String currency, String description) {}

    /** redirectUrl : la page de paiement vers laquelle on envoie le client. */
    record CheckoutSession(String token, String redirectUrl) {}
}