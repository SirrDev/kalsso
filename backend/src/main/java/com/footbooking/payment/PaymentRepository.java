package com.footbooking.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByProviderAndProviderRef(String provider, String providerRef);

    List<Payment> findByBookingId(UUID bookingId);

    Optional<Payment> findFirstByBookingIdAndStatusOrderByCreatedAtDesc(UUID bookingId, PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findForUpdateByProviderAndProviderRef(String provider, String providerRef);

    List<Payment> findByBookingIdAndStatus(UUID bookingId, PaymentStatus status);

    List<Payment> findTop100ByStatusOrderByCreatedAtAsc(PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findForUpdateById(UUID id);

    /** Remboursements a tenter : en attente, sous la limite de tentatives, derniere tentative assez ancienne. */
    @Query("""
            select p.id from Payment p
            where p.status = com.footbooking.payment.PaymentStatus.REFUND_PENDING
              and p.refundAttempts < :max
              and (p.refundLastAttemptAt is null or p.refundLastAttemptAt <= :before)
            order by p.createdAt
            """)
    List<UUID> findRefundDueIds(@Param("max") int max, @Param("before") Instant before, Pageable pageable);
}