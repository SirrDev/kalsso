package com.footbooking.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByProviderAndProviderRef(String provider, String providerRef);
    List<Payment> findByBookingId(UUID bookingId);
    Optional<Payment> findFirstByBookingIdAndStatusOrderByCreatedAtDesc(UUID bookingId, PaymentStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findForUpdateByProviderAndProviderRef(String provider, String providerRef);

}