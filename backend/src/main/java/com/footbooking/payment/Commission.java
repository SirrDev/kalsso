package com.footbooking.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Calcul de la commission de la plateforme. Fonction pure, sans dependance. */
public final class Commission {
    private Commission() {}

    public record Split(BigDecimal commission, BigDecimal net) {}

    public static Split split(BigDecimal amount, BigDecimal percent, String currency) {
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Montant negatif");
        }
        if (percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Pourcentage de commission invalide");
        }
        int scale = "XOF".equals(currency) ? 0 : 2;
        BigDecimal commission = amount.multiply(percent)
                .divide(BigDecimal.valueOf(100), scale, RoundingMode.HALF_UP);
        return new Split(commission, amount.subtract(commission));
    }
}