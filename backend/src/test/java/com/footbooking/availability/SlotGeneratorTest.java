package com.footbooking.availability;

import com.footbooking.availability.SlotGenerator.Range;
import com.footbooking.availability.SlotGenerator.Slot;
import com.footbooking.availability.SlotGenerator.Status;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SlotGeneratorTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 10);   // un samedi
    private static final ZoneId DAKAR = ZoneId.of("Africa/Dakar");
    private static final Instant LONG_AGO = Instant.parse("2020-01-01T00:00:00Z");

    private static Range range(String open, String close) {
        return new Range(LocalTime.parse(open), LocalTime.parse(close));
    }

    private static List<Slot> generate(List<Range> ranges, int minutes, ZoneId zone, Instant now) {
        return SlotGenerator.generate(DAY, ranges, minutes, zone, now);
    }

    @Test
    void oneRange_hourlySlots_fillsTheRange() {
        List<Slot> slots = generate(List.of(range("18:00", "23:00")), 60, DAKAR, LONG_AGO);

        assertEquals(5, slots.size());
        assertEquals(LocalTime.of(18, 0), slots.get(0).localStart());
        assertEquals(LocalTime.of(22, 0), slots.get(4).localStart());
        assertEquals(LocalTime.of(23, 0), slots.get(4).localEnd());
    }

    @Test
    void ninetyMinuteSlots_lastPartialSlotIsDropped() {
        List<Slot> slots = generate(List.of(range("18:00", "23:00")), 90, DAKAR, LONG_AGO);

        assertEquals(List.of(LocalTime.of(18, 0), LocalTime.of(19, 30), LocalTime.of(21, 0)),
                slots.stream().map(Slot::localStart).toList());
    }

    @Test
    void remainderShorterThanASlot_isDropped() {
        List<Slot> slots = generate(List.of(range("18:00", "20:30")), 60, DAKAR, LONG_AGO);

        assertEquals(2, slots.size());
        assertEquals(LocalTime.of(20, 0), slots.get(1).localEnd());
    }

    @Test
    void twoRanges_areBothUsedAndSorted() {
        // envoyees dans le desordre volontairement
        List<Slot> slots = generate(
                List.of(range("16:00", "20:00"), range("08:00", "12:00")), 60, DAKAR, LONG_AGO);

        assertEquals(8, slots.size());
        assertEquals(LocalTime.of(8, 0), slots.get(0).localStart());
        assertEquals(LocalTime.of(16, 0), slots.get(4).localStart());
    }

    @Test
    void noRange_returnsNoSlot() {
        assertTrue(generate(List.of(), 60, DAKAR, LONG_AGO).isEmpty());
    }

    @Test
    void slotsAlreadyStarted_arePast() {
        Instant now = Instant.parse("2026-10-10T19:30:00Z");   // Dakar = UTC+0
        List<Slot> slots = generate(List.of(range("18:00", "23:00")), 60, DAKAR, now);

        assertEquals(List.of(Status.PAST, Status.PAST, Status.AVAILABLE, Status.AVAILABLE, Status.AVAILABLE),
                slots.stream().map(Slot::status).toList());
    }

    @Test
    void localTimeIsConvertedWithTheVenueTimezone() {
        // Paris est en heure d'ete (UTC+2) le 10 octobre : 18h locale = 16h UTC
        List<Slot> slots = generate(List.of(range("18:00", "19:00")), 60,
                ZoneId.of("Europe/Paris"), LONG_AGO);

        assertEquals(Instant.parse("2026-10-10T16:00:00Z"), slots.get(0).start());
        assertEquals(Instant.parse("2026-10-10T17:00:00Z"), slots.get(0).end());
    }

    @Test
    void nonPositiveSlotDuration_isRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> generate(List.of(range("18:00", "23:00")), 0, DAKAR, LONG_AGO));
    }
}