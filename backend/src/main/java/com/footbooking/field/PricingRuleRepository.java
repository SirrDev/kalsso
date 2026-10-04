package com.footbooking.field;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {
    List<PricingRule> findByFieldIdOrderByDayOfWeekAscStartTimeAsc(UUID fieldId);
    Optional<PricingRule> findByIdAndFieldId(UUID id, UUID fieldId);
}