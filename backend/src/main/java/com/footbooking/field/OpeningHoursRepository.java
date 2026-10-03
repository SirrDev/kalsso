package com.footbooking.field;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OpeningHoursRepository extends JpaRepository<OpeningHours, UUID> {
    List<OpeningHours> findByFieldIdOrderByDayOfWeekAscOpenTimeAsc(UUID fieldId);
    void deleteByFieldId(UUID fieldId);
}