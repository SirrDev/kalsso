package com.footbooking.payment;

import com.footbooking.payment.Commission.Split;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CommissionTest {

    private static final BigDecimal THREE = new BigDecimal("3");

    /** compareTo : 240 et 240.00 sont le meme montant. */
    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "attendu " + expected + " mais obtenu " + actual);
    }

    @Test
    void threePercentOf8000() {
        Split s = Commission.split(new BigDecimal("8000"), THREE, "XOF");

        assertAmount("240", s.commission());
        assertAmount("7760", s.net());
    }

    @Test
    void roundsHalfUp() {
        Split s = Commission.split(new BigDecimal("5017"), THREE, "XOF");   // 150,51

        assertAmount("151", s.commission());
        assertAmount("4866", s.net());
    }

    @Test
    void roundsDown() {
        Split s = Commission.split(new BigDecimal("5001"), THREE, "XOF");   // 150,03

        assertAmount("150", s.commission());
        assertAmount("4851", s.net());
    }

    @Test
    void commissionPlusNetAlwaysEqualsAmount() {
        BigDecimal amount = new BigDecimal("7777.00");

        Split s = Commission.split(amount, THREE, "XOF");

        assertEquals(0, amount.compareTo(s.commission().add(s.net())));
    }

    @Test
    void zeroAmount_givesZeroCommission() {
        Split s = Commission.split(BigDecimal.ZERO, THREE, "XOF");

        assertAmount("0", s.commission());
        assertAmount("0", s.net());
    }

    @Test
    void zeroPercent_givesNoCommission() {
        Split s = Commission.split(new BigDecimal("8000"), BigDecimal.ZERO, "XOF");

        assertAmount("0", s.commission());
        assertAmount("8000", s.net());
    }

    @Test
    void nonXofCurrency_keepsTheCents() {
        Split s = Commission.split(new BigDecimal("10.01"), THREE, "EUR");  // 0,3003

        assertAmount("0.30", s.commission());
        assertAmount("9.71", s.net());
    }

    @Test
    void invalidPercentOrAmount_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> Commission.split(new BigDecimal("8000"), new BigDecimal("-1"), "XOF"));
        assertThrows(IllegalArgumentException.class,
                () -> Commission.split(new BigDecimal("8000"), new BigDecimal("101"), "XOF"));
        assertThrows(IllegalArgumentException.class,
                () -> Commission.split(new BigDecimal("-5"), THREE, "XOF"));
    }
}