package com.footbooking.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    /**
     * Reservations qui occupent le terrain sur [from, to[ :
     * CONFIRMED, ou PENDING dont le blocage n'a pas expire.
     */
    @Query("""
            select b from Booking b
            where b.fieldId = :fieldId
              and b.startTime < :to and b.endTime > :from
              and (b.status = com.footbooking.booking.BookingStatus.CONFIRMED
                   or (b.status = com.footbooking.booking.BookingStatus.PENDING and b.expiresAt > :now))
            order by b.startTime
            """)
    List<Booking> findOccupying(@Param("fieldId") UUID fieldId,
                                @Param("from") Instant from,
                                @Param("to") Instant to,
                                @Param("now") Instant now);
}