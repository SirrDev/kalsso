package com.footbooking.payment;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/payments")
@PreAuthorize("hasRole('ADMIN')")
public class AdminPaymentController {

    public record RefundPendingResponse(UUID paymentId, UUID bookingId, BigDecimal amount, String currency,
                                        int attempts, Instant lastAttemptAt, Instant paidAt) {}

    public record RefundAttemptResponse(RefundService.Outcome outcome) {}

    private final RefundService service;

    public AdminPaymentController(RefundService service) {
        this.service = service;
    }

    @GetMapping("/refunds")
    public List<RefundPendingResponse> pendingRefunds() {
        return service.listPending().stream()
                .map(p -> new RefundPendingResponse(p.getId(), p.getBookingId(), p.getAmount(), p.getCurrency(),
                        p.getRefundAttempts(), p.getRefundLastAttemptAt(), p.getPaidAt()))
                .toList();
    }

    /** Force une tentative, meme apres abandon automatique. */
    @PostMapping("/{paymentId}/refund")
    public RefundAttemptResponse refund(@PathVariable UUID paymentId) {
        return new RefundAttemptResponse(service.attempt(paymentId, true));
    }
}