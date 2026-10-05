package com.footbooking.payment;

import com.footbooking.common.CurrentUser;
import com.footbooking.payment.PaymentDtos.PaymentResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/bookings/{bookingId}/pay")
@PreAuthorize("hasRole('CLIENT')")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping
    public PaymentResponse pay(Authentication auth, @PathVariable UUID bookingId) {
        return service.initiate(CurrentUser.id(auth), bookingId);
    }
}