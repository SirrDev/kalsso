package com.footbooking.availability;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Decoupe des plages d'ouverture en creneaux. Fonction pure : aucune dependance Spring ou base. */
public final class SlotGenerator {
    private SlotGenerator() {}

    public enum Status { AVAILABLE, PAST }   // BOOKED sera ajoute avec les reservations (B4)

    public record Range(LocalTime open, LocalTime close) {}

    public record Slot(Instant start, Instant end, LocalTime localStart, LocalTime localEnd, Status status) {}

    /**
     * @param date         jour demande (date locale du complexe)
     * @param ranges       plages d'ouverture de ce jour, en heure locale
     * @param slotMinutes  duree d'un creneau
     * @param zone         fuseau du complexe
     * @param now          instant courant (injecte pour pouvoir tester)
     */
    public static List<Slot> generate(LocalDate date, List<Range> ranges, int slotMinutes,
                                      ZoneId zone, Instant now) {
        if (slotMinutes <= 0) {
            throw new IllegalArgumentException("slotMinutes doit etre positif");
        }
        List<Slot> slots = new ArrayList<>();
        List<Range> sorted = ranges.stream().sorted(Comparator.comparing(Range::open)).toList();

        for (Range r : sorted) {
            int openMin = r.open().toSecondOfDay() / 60;
            int closeMin = r.close().toSecondOfDay() / 60;
            // Calcul en minutes : evite qu'une heure ne "boucle" apres minuit
            for (int m = openMin; m + slotMinutes <= closeMin; m += slotMinutes) {
                LocalTime localStart = LocalTime.ofSecondOfDay(m * 60L);
                LocalTime localEnd = LocalTime.ofSecondOfDay((m + slotMinutes) * 60L);
                Instant start = date.atTime(localStart).atZone(zone).toInstant();
                Instant end = date.atTime(localEnd).atZone(zone).toInstant();
                Status status = start.isAfter(now) ? Status.AVAILABLE : Status.PAST;
                slots.add(new Slot(start, end, localStart, localEnd, status));
            }
        }
        return slots;
    }
}