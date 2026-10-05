package com.footbooking.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/**
 * Verification de l'authenticite d'une notification PayTech (IPN). Fonction pure, sans dependance.
 * Seule la signature HMAC est acceptee : la comparaison des hachages des cles est refusee
 * car ces hachages sont identiques dans toutes les notifications (donc rejouables).
 */
public final class PayTechSignature {
    private PayTechSignature() {}

    public static boolean isAuthentic(Map<String, String> params, String apiKey, String apiSecret) {
        // Sans cles configurees, aucune notification ne peut etre authentifiee
        if (isBlank(apiKey) || isBlank(apiSecret)) {
            return false;
        }
        String received = clean(params.get("hmac_compute"));
        String reference = clean(params.get("ref_command"));
        // Valeurs textuelles exactement telles que recues : on ne reformate jamais le montant
        String price = firstNonBlank(params.get("final_item_price"), params.get("item_price"));
        if (received == null || reference == null || price == null) {
            return false;
        }

        String expected = hmacHex(price + "|" + reference + "|" + apiKey, apiSecret);
        // Comparaison en temps constant : la duree ne revele rien sur la valeur attendue
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                received.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    static String hmacHex(String message, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponible", e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String clean(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private static String firstNonBlank(String a, String b) {
        String first = clean(a);
        return first != null ? first : clean(b);
    }
}