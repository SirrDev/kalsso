package com.footbooking.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

    /** Libere les blocages expires qui occupent encore ce creneau (ils bloquent via la contrainte SQL). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Booking b set b.status = com.footbooking.booking.BookingStatus.CANCELLED
            where b.fieldId = :fieldId
              and b.status = com.footbooking.booking.BookingStatus.PENDING
              and b.expiresAt <= :now
              and b.startTime < :to and b.endTime > :from
            """)
    int cancelExpiredHolds(@Param("fieldId") UUID fieldId,
                           @Param("from") Instant from,
                           @Param("to") Instant to,
                           @Param("now") Instant now);

    /** Nettoyage global : annule tous les blocages expires, tous terrains confondus. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Booking b set b.status = com.footbooking.booking.BookingStatus.CANCELLED
            where b.status = com.footbooking.booking.BookingStatus.PENDING
              and b.expiresAt <= :now
            """)
    int cancelAllExpiredHolds(@Param("now") Instant now);

    List<Booking> findTop100ByUserIdOrderByStartTimeDesc(UUID userId);

    Optional<Booking> findByIdAndUserId(UUID id, UUID userId);

    /** Reservations visibles par le proprietaire : on ecarte les annulees et les blocages expires. */
    @Query("""
            select b from Booking b
            where b.fieldId = :fieldId
              and b.startTime < :to and b.endTime > :from
              and (b.status in (com.footbooking.booking.BookingStatus.CONFIRMED,
                                com.footbooking.booking.BookingStatus.COMPLETED,
                                com.footbooking.booking.BookingStatus.NO_SHOW)
                   or (b.status = com.footbooking.booking.BookingStatus.PENDING and b.expiresAt > :now))
            order by b.startTime
            """)
    List<Booking> findForOwner(@Param("fieldId") UUID fieldId,
                               @Param("from") Instant from,
                               @Param("to") Instant to,
                               @Param("now") Instant now);

    /** Confirme une reservation payee (en attente ou deja liberee). 0 ligne = deja dans un etat final. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Booking b set b.status = com.footbooking.booking.BookingStatus.CONFIRMED, b.expiresAt = null
            where b.id = :id
              and b.status in (com.footbooking.booking.BookingStatus.PENDING,
                               com.footbooking.booking.BookingStatus.CANCELLED)
            """)
    int confirmPaidBooking(@Param("id") UUID id);

    /** Reservations CONFIRMED avec client dont le debut tombe dans [from, to[ (pour les rappels). */
    @Query("""
            select b from Booking b
            where b.status = com.footbooking.booking.BookingStatus.CONFIRMED
              and b.userId is not null
              and b.startTime >= :from and b.startTime < :to
            order by b.startTime
            """)
    List<Booking> findConfirmedStartingBetween(@Param("from") Instant from, @Param("to") Instant to);
}