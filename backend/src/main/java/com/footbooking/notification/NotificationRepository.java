package com.footbooking.notification;

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

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByDedupeKey(String dedupeKey);

    /** Notifications a envoyer : PENDING dont l'heure d'envoi est passee, les plus anciennes d'abord. */
    @Query("""
            select n.id from Notification n
            where n.status = com.footbooking.notification.NotificationStatus.PENDING
              and n.sendAt <= :now
            order by n.sendAt
            """)
    List<UUID> findDueIds(@Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Notification> findForUpdateById(UUID id);
}