package com.footbooking.payment;

import com.footbooking.common.ApiException;
import com.footbooking.payment.PaymentProvider.CheckoutRequest;
import com.footbooking.payment.PaymentProvider.CheckoutSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PayTechClientTest {

    private static final String IPN = "https://api.example.com/api/payments/paytech/ipn";

    private static CheckoutRequest request(String amount) {
        return new CheckoutRequest("a1b2c3d4e5f60718", "Terrain 1", new BigDecimal(amount), "XOF",
                "Reservation Stade Pikine");
    }

    private static HttpStatus statusOf(Runnable action) {
        return assertThrows(ApiException.class, action::run).getStatus();
    }

    // ---------- buildBody ----------

    @Test
    void buildBody_containsTheRequiredFields() {
        Map<String, Object> body = PayTechClient.buildBody(request("8000.00"), "test", IPN,
                "https://app.example.com/ok", "https://app.example.com/ko");

        assertEquals("Terrain 1", body.get("item_name"));
        assertEquals(8000L, body.get("item_price"));
        assertEquals("XOF", body.get("currency"));
        assertEquals("a1b2c3d4e5f60718", body.get("ref_command"));
        assertEquals("Reservation Stade Pikine", body.get("command_name"));
        assertEquals("test", body.get("env"));
        assertEquals(IPN, body.get("ipn_url"));
        assertEquals("https://app.example.com/ok", body.get("success_url"));
        assertEquals("https://app.example.com/ko", body.get("cancel_url"));
    }

    @Test
    void buildBody_keepsDecimalsWhenTheAmountHasThem() {
        Map<String, Object> body = PayTechClient.buildBody(request("10.01"), "test", IPN, "", "");

        assertEquals(new BigDecimal("10.01"), body.get("item_price"));
    }

    @Test
    void buildBody_omitsEmptyOptionalUrls() {
        Map<String, Object> body = PayTechClient.buildBody(request("8000"), "test", IPN, "", "");

        assertFalse(body.containsKey("success_url"));
        assertFalse(body.containsKey("cancel_url"));
    }

    @Test
    void buildBody_rejectsANonHttpsNotificationUrl() {
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, statusOf(() ->
                PayTechClient.buildBody(request("8000"), "test", "http://localhost:8080/ipn", "", "")));
    }

    // ---------- parseResponse ----------

    @Test
    void parseResponse_success() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", 1);
        response.put("token", "40j515fgrkynl56hi");
        response.put("redirect_url", "https://paytech.sn/payment/checkout/40j515fgrkynl56hi");

        CheckoutSession session = PayTechClient.parseResponse(response);

        assertEquals("40j515fgrkynl56hi", session.token());
        assertEquals("https://paytech.sn/payment/checkout/40j515fgrkynl56hi", session.redirectUrl());
    }

    @Test
    void parseResponse_refusal_returns502() {
        Map<String, Object> response = Map.of("success", -1, "message", "cle api invalide");

        assertEquals(HttpStatus.BAD_GATEWAY, statusOf(() -> PayTechClient.parseResponse(response)));
    }

    @Test
    void parseResponse_missingToken_returns502() {
        Map<String, Object> response = Map.of("success", 1, "redirect_url", "https://paytech.sn/payment/checkout/x");

        assertEquals(HttpStatus.BAD_GATEWAY, statusOf(() -> PayTechClient.parseResponse(response)));
    }

    @Test
    void parseResponse_nonHttpsRedirect_returns502() {
        Map<String, Object> response = Map.of("success", 1, "token", "abc", "redirect_url", "http://evil.example/pay");

        assertEquals(HttpStatus.BAD_GATEWAY, statusOf(() -> PayTechClient.parseResponse(response)));
    }

    @Test
    void parseResponse_nullBody_returns502() {
        assertEquals(HttpStatus.BAD_GATEWAY, statusOf(() -> PayTechClient.parseResponse(null)));
    }

    // ---------- configuration ----------

    @Test
    void createCheckout_withoutKeys_returns503BeforeAnyNetworkCall() {
        PayTechClient client = new PayTechClient("https://paytech.sn/api", "", "", "test", IPN, "", "");

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, statusOf(() -> client.createCheckout(request("8000"))));
    }

    @Test
    void constructor_rejectsAnUnknownEnvironment() {
        assertThrows(IllegalStateException.class,
                () -> new PayTechClient("https://paytech.sn/api", "k", "s", "production", IPN, "", ""));
    }
}