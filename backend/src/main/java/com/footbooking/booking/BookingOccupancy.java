package com.footbooking.booking;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Lecture seule : quelles plages d'un terrain sont occupees ? Ne depend pas des autres services. */
@Service
public class BookingOccupancy {

    public record Occupied(Instant start, Instant end) {}

    private final BookingRepository bookings;
    private final Clock clock;

    public BookingOccupancy(BookingRepository bookings, Clock clock) {
        this.bookings = bookings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Occupied> forField(UUID fieldId, Instant from, Instant to) {
        return bookings.findOccupying(fieldId, from, to, clock.instant()).stream()
                .map(b -> new Occupied(b.getStartTime(), b.getEndTime()))
                .toList();
    }
}