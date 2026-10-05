package com.footbooking.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/payments/paytech")
public class PayTechIpnController {

    private final PaymentIpnService service;

    public PayTechIpnController(PaymentIpnService service) {
        this.service = service;
    }

    /** Public : l'authenticite est verifiee par la signature, pas par un token. PayTech envoie un formulaire. */
    @PostMapping("/ipn")
    public ResponseEntity<String> ipn(@RequestParam Map<String, String> params) {
        service.handle(params);
        return ResponseEntity.ok("IPN OK");
    }
}