package com.footbooking.payment;

import com.footbooking.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "app.payment.provider", havingValue = "paytech", matchIfMissing = true)
public class PayTechClient implements PaymentProvider {

    private static final Logger log = LoggerFactory.getLogger(PayTechClient.class);
    private static final Set<String> ENVIRONMENTS = Set.of("test", "prod");

    private final RestClient http;
    private final String apiKey;
    private final String apiSecret;
    private final String env;
    private final String ipnUrl;
    private final String successUrl;
    private final String cancelUrl;

    public PayTechClient(@Value("${app.paytech.base-url}") String baseUrl,
                         @Value("${app.paytech.api-key:}") String apiKey,
                         @Value("${app.paytech.api-secret:}") String apiSecret,
                         @Value("${app.paytech.env:test}") String env,
                         @Value("${app.paytech.ipn-url:}") String ipnUrl,
                         @Value("${app.paytech.success-url:}") String successUrl,
                         @Value("${app.paytech.cancel-url:}") String cancelUrl) {
        // Echec au demarrage plutot qu'un debit reel provoque par une faute de frappe
        if (!ENVIRONMENTS.contains(env)) {
            throw new IllegalStateException("app.paytech.env doit valoir 'test' ou 'prod' (valeur : " + env + ")");
        }
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.env = env;
        this.ipnUrl = ipnUrl;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @Override
    public String name() {
        return "PAYTECH";
    }

    @Override
    public CheckoutSession createCheckout(CheckoutRequest request) {
        requireConfigured();
        Map<String, Object> body = buildBody(request, env, ipnUrl, successUrl, cancelUrl);

        Map<String, Object> response;
        try {
            response = http.post()
                    .uri("/payment/request-payment")
                    .header("API_KEY", apiKey)
                    .header("API_SECRET", apiSecret)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientException e) {
            // On ne journalise jamais les en-tetes de la requete (elles contiennent les cles)
            log.error("Appel PayTech en echec : {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Le fournisseur de paiement est indisponible");
        }
        return parseResponse(response);
    }

    @Override
    public RefundResult refund(String reference) {
        requireConfigured();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("ref_command", reference);

        Map<String, Object> response;
        try {
            response = http.post()
                    .uri("/payment/refund-payment")
                    .header("API_KEY", apiKey)
                    .header("API_SECRET", apiSecret)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (RestClientException e) {
            log.error("Appel de remboursement PayTech en echec : {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Le fournisseur de paiement est indisponible");
        }
        return parseRefundResponse(response);
    }

    static RefundResult parseRefundResponse(Map<String, Object> response) {
        if (response == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Reponse du fournisseur de paiement invalide");
        }
        Object message = response.get("message");
        return new RefundResult(isSuccess(response.get("success")), message == null ? "" : message.toString());
    }

    private void requireConfigured() {
        if (apiKey.isBlank() || apiSecret.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Le paiement n'est pas configure");
        }
    }

    // ---------- Parties pures (testables sans reseau) ----------

    static Map<String, Object> buildBody(CheckoutRequest r, String env, String ipnUrl,
                                         String successUrl, String cancelUrl) {
        // PayTech n'accepte que du HTTPS pour la notification
        if (!ipnUrl.startsWith("https://")) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "L'URL de notification du paiement doit etre en HTTPS");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("item_name", r.itemName());
        body.put("item_price", price(r.amount()));
        body.put("currency", r.currency());
        body.put("ref_command", r.reference());
        body.put("command_name", r.description());
        body.put("env", env);
        body.put("ipn_url", ipnUrl);
        if (!successUrl.isBlank()) {
            body.put("success_url", successUrl);
        }
        if (!cancelUrl.isBlank()) {
            body.put("cancel_url", cancelUrl);
        }
        return body;
    }

    /** 8000.00 est envoye comme 8000 (entier) ; 10.01 garde ses decimales. */
    static Object price(BigDecimal amount) {
        BigDecimal stripped = amount.stripTrailingZeros();
        return stripped.scale() <= 0 ? (Object) stripped.longValueExact() : stripped;
    }

    static CheckoutSession parseResponse(Map<String, Object> response) {
        if (response == null || !isSuccess(response.get("success"))) {
            Object message = response == null ? null : response.get("message");
            log.warn("PayTech a refuse la demande : {}", message);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Le fournisseur de paiement a refuse la demande");
        }
        String token = asText(response.get("token"));
        String redirectUrl = asText(response.get("redirect_url"));
        // On n'envoie jamais un client vers une adresse qui n'est pas en HTTPS
        if (token == null || redirectUrl == null || !redirectUrl.startsWith("https://")) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Reponse du fournisseur de paiement invalide");
        }
        return new CheckoutSession(token, redirectUrl);
    }

    private static boolean isSuccess(Object value) {
        return (value instanceof Number n && n.intValue() == 1) || Boolean.TRUE.equals(value);
    }

    private static String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}