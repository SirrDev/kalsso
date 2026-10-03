package com.footbooking.venue;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {
    List<Venue> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
