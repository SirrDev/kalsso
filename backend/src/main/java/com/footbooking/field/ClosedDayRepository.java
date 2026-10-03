package com.footbooking.field;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClosedDayRepository extends JpaRepository<ClosedDay, UUID> {
    List<ClosedDay> findByFieldIdOrderByDayAsc(UUID fieldId);
    Optional<ClosedDay> findByIdAndFieldId(UUID id, UUID fieldId);
    boolean existsByFieldIdAndDay(UUID fieldId, LocalDate day);
}