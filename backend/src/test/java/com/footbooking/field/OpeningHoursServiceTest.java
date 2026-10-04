package com.footbooking.field;

import com.footbooking.common.ApiException;
import com.footbooking.field.OpeningHoursDtos.OpeningSlot;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OpeningHoursServiceTest {

    private static OpeningSlot slot(int day, String open, String close) {
        return new OpeningSlot(day, LocalTime.parse(open), LocalTime.parse(close));
    }

    @Test
    void validWeek_isAccepted() {
        assertDoesNotThrow(() -> OpeningHoursService.validate(List.of(
                slot(1, "16:00", "23:00"),
                slot(6, "08:00", "23:00"))));
    }

    @Test
    void emptyWeek_isAccepted() {
        assertDoesNotThrow(() -> OpeningHoursService.validate(List.of()));
    }

    @Test
    void closeBeforeOpen_returns400() {
        ApiException ex = assertThrows(ApiException.class, () ->
                OpeningHoursService.validate(List.of(slot(1, "20:00", "16:00"))));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void closeEqualsOpen_returns400() {
        ApiException ex = assertThrows(ApiException.class, () ->
                OpeningHoursService.validate(List.of(slot(1, "16:00", "16:00"))));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void overlappingSlotsSameDay_returns400() {
        ApiException ex = assertThrows(ApiException.class, () ->
                OpeningHoursService.validate(List.of(
                        slot(1, "16:00", "20:00"),
                        slot(1, "19:00", "23:00"))));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void overlappingSlots_areDetected_evenIfSentOutOfOrder() {
        assertThrows(ApiException.class, () ->
                OpeningHoursService.validate(List.of(
                        slot(1, "19:00", "23:00"),
                        slot(1, "16:00", "20:00"))));
    }

    @Test
    void touchingSlotsSameDay_areAccepted() {
        assertDoesNotThrow(() -> OpeningHoursService.validate(List.of(
                slot(1, "08:00", "12:00"),
                slot(1, "12:00", "18:00"))));
    }

    @Test
    void sameHoursOnDifferentDays_areAccepted() {
        assertDoesNotThrow(() -> OpeningHoursService.validate(List.of(
                slot(1, "16:00", "23:00"),
                slot(2, "16:00", "23:00"))));
    }
}