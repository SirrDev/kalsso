package com.footbooking.field;

import com.footbooking.common.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PricingServiceTest {

    private static PricingRule rule(Integer day, String start, String end, String price) {
        PricingRule r = new PricingRule();
        r.setDayOfWeek(day);
        r.setStartTime(LocalTime.parse(start));
        r.setEndTime(LocalTime.parse(end));
        r.setPrice(new BigDecimal(price));
        r.setCurrency("XOF");
        return r;
    }

    private static LocalTime t(String s) {
        return LocalTime.parse(s);
    }

    // ---------- resolve : quel tarif s'applique ? ----------

    private final List<PricingRule> rules = List.of(
            rule(null, "16:00", "23:00", "5000"),   // tous les jours
            rule(6, "16:00", "23:00", "8000"));     // samedi

    @Test
    void resolve_specificDayBeatsEveryDay() {
        Optional<PricingRule> r = PricingService.resolve(rules, 6, t("18:00"));
        assertEquals(new BigDecimal("8000"), r.orElseThrow().getPrice());
    }

    @Test
    void resolve_everyDayRuleAppliesOnOtherDays() {
        Optional<PricingRule> r = PricingService.resolve(rules, 2, t("18:00"));
        assertEquals(new BigDecimal("5000"), r.orElseThrow().getPrice());
    }

    @Test
    void resolve_startTimeIsIncluded() {
        assertTrue(PricingService.resolve(rules, 2, t("16:00")).isPresent());
    }

    @Test
    void resolve_endTimeIsExcluded() {
        assertTrue(PricingService.resolve(rules, 2, t("23:00")).isEmpty());
    }

    @Test
    void resolve_lastSlotBeforeEndIsCovered() {
        assertTrue(PricingService.resolve(rules, 2, t("22:00")).isPresent());
    }

    @Test
    void resolve_beforeAnyRule_returnsEmpty() {
        assertTrue(PricingService.resolve(rules, 2, t("10:00")).isEmpty());
    }

    @Test
    void resolve_ruleOfAnotherDayDoesNotApply() {
        List<PricingRule> onlySaturday = List.of(rule(6, "16:00", "23:00", "8000"));
        assertTrue(PricingService.resolve(onlySaturday, 3, t("18:00")).isEmpty());
    }

    // ---------- validate : quelles regles accepte-t-on ? ----------

    @Test
    void validate_endBeforeStart_returns400() {
        ApiException ex = assertThrows(ApiException.class, () ->
                PricingService.validate(null, t("20:00"), t("16:00"), "XOF", List.of()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void validate_overlapSameScope_returns409() {
        List<PricingRule> existing = List.of(rule(null, "16:00", "23:00", "5000"));
        ApiException ex = assertThrows(ApiException.class, () ->
                PricingService.validate(null, t("20:00"), t("23:00"), "XOF", existing));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void validate_specificDayMayOverlapEveryDayRule() {
        List<PricingRule> existing = List.of(rule(null, "16:00", "23:00", "5000"));
        assertDoesNotThrow(() ->
                PricingService.validate(6, t("16:00"), t("23:00"), "XOF", existing));
    }

    @Test
    void validate_touchingRanges_areAccepted() {
        List<PricingRule> existing = List.of(rule(null, "08:00", "16:00", "3000"));
        assertDoesNotThrow(() ->
                PricingService.validate(null, t("16:00"), t("23:00"), "XOF", existing));
    }

    @Test
    void validate_differentCurrency_returns400() {
        List<PricingRule> existing = List.of(rule(null, "08:00", "12:00", "3000"));
        ApiException ex = assertThrows(ApiException.class, () ->
                PricingService.validate(null, t("16:00"), t("23:00"), "EUR", existing));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }
}