package com.footbooking.payment;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PayTechSignatureTest {

    private static final String KEY = "test-key";
    private static final String SECRET = "test-secret";
    private static final String REF = "a1b2c3d4e5f60718a1b2c3d4e5f60718";

    // Valeurs calculees independamment avec Python (hmac + hashlib), pas avec ce code
    private static final String SIG_8000 = "f164db570854451bd813a5db1bb2949c7d61cdc6c3d5a6dd72cba3653138b1e7";
    private static final String SIG_7500 = "96aaeca182606d97af410c04b7ef9a55b0c653a97eaa81efa836d66b6ab03cb0";
    private static final String SIG_WRONG_SECRET = "710c47f3736dc532a06522ef46b032712c664705b240ded88539a5ed7b4da0dd";

    private static boolean check(Map<String, String> params) {
        return PayTechSignature.isAuthentic(params, KEY, SECRET);
    }

    @Test
    void validSignature_isAccepted() {
        assertTrue(check(Map.of("item_price", "8000", "ref_command", REF, "hmac_compute", SIG_8000)));
    }

    @Test
    void finalItemPrice_takesPrecedenceOverItemPrice() {
        assertTrue(check(Map.of("final_item_price", "7500", "item_price", "8000",
                "ref_command", REF, "hmac_compute", SIG_7500)));
    }

    @Test
    void blankFinalItemPrice_fallsBackToItemPrice() {
        assertTrue(check(Map.of("final_item_price", "", "item_price", "8000",
                "ref_command", REF, "hmac_compute", SIG_8000)));
    }

    @Test
    void tamperedAmount_isRejected() {
        assertFalse(check(Map.of("item_price", "100", "ref_command", REF, "hmac_compute", SIG_8000)));
    }

    @Test
    void tamperedReference_isRejected() {
        assertFalse(check(Map.of("item_price", "8000", "ref_command", "autre-reference",
                "hmac_compute", SIG_8000)));
    }

    @Test
    void signatureMadeWithAnotherSecret_isRejected() {
        assertFalse(check(Map.of("item_price", "8000", "ref_command", REF, "hmac_compute", SIG_WRONG_SECRET)));
    }

    @Test
    void missingHmac_isRejected_evenWithTheWeakerSha256Fields() {
        assertFalse(check(Map.of("item_price", "8000", "ref_command", REF)));
        assertFalse(check(Map.of("item_price", "8000", "ref_command", REF,
                "api_key_sha256", "abc", "api_secret_sha256", "def")));
    }

    @Test
    void uppercaseHex_isAccepted() {
        assertTrue(check(Map.of("item_price", "8000", "ref_command", REF,
                "hmac_compute", SIG_8000.toUpperCase(Locale.ROOT))));
    }

    @Test
    void missingReferenceOrPrice_isRejected() {
        assertFalse(check(Map.of("item_price", "8000", "hmac_compute", SIG_8000)));
        assertFalse(check(Map.of("ref_command", REF, "hmac_compute", SIG_8000)));
    }

    @Test
    void unconfiguredKeys_rejectEverything() {
        Map<String, String> params = Map.of("item_price", "8000", "ref_command", REF, "hmac_compute", SIG_8000);

        assertFalse(PayTechSignature.isAuthentic(params, "", SECRET));
        assertFalse(PayTechSignature.isAuthentic(params, KEY, ""));
        assertFalse(PayTechSignature.isAuthentic(params, null, null));
    }
}