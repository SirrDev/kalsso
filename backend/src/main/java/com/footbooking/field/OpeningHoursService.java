package com.footbooking.field;

import com.footbooking.common.ApiException;
import com.footbooking.field.OpeningHoursDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class OpeningHoursService {

    private final FieldService fieldService;
    private final OpeningHoursRepository hoursRepo;
    private final ClosedDayRepository closedRepo;

    public OpeningHoursService(FieldService fieldService,
                               OpeningHoursRepository hoursRepo,
                               ClosedDayRepository closedRepo) {
        this.fieldService = fieldService;
        this.hoursRepo = hoursRepo;
        this.closedRepo = closedRepo;
    }

    // ---------- Horaires ----------

    @Transactional(readOnly = true)
    public List<OpeningSlotResponse> getWeek(UUID ownerId, UUID fieldId) {
        fieldService.getOwned(ownerId, fieldId);
        return hoursRepo.findByFieldIdOrderByDayOfWeekAscOpenTimeAsc(fieldId)
                .stream().map(OpeningSlotResponse::from).toList();
    }

    public record DayRange(LocalTime open, LocalTime close) {}

    /**
     * Plages d'ouverture d'un terrain pour une date donnee (heure locale du complexe).
     * Liste vide si le terrain est ferme ce jour-la (jour de fermeture ou aucun horaire).
     */
    @Transactional(readOnly = true)
    public List<DayRange> rangesFor(UUID fieldId, LocalDate date) {
        if (closedRepo.existsByFieldIdAndDay(fieldId, date)) {
            return List.of();
        }
        int dayOfWeek = date.getDayOfWeek().getValue();   // 1 = lundi ... 7 = dimanche
        return hoursRepo.findByFieldIdOrderByDayOfWeekAscOpenTimeAsc(fieldId).stream()
                .filter(h -> h.getDayOfWeek() == dayOfWeek)
                .map(h -> new DayRange(h.getOpenTime(), h.getCloseTime()))
                .toList();
    }

    /** Remplace toute la semaine. Un jour sans plage = terrain ferme ce jour-la. */
    @Transactional
    public List<OpeningSlotResponse> replaceWeek(UUID ownerId, UUID fieldId, List<OpeningSlot> slots) {
        fieldService.getOwned(ownerId, fieldId);
        validate(slots);

        hoursRepo.deleteByFieldId(fieldId);
        hoursRepo.flush();

        List<OpeningHours> entities = slots.stream().map(s -> {
            OpeningHours h = new OpeningHours();
            h.setFieldId(fieldId);
            h.setDayOfWeek(s.dayOfWeek());
            h.setOpenTime(s.openTime());
            h.setCloseTime(s.closeTime());
            return h;
        }).toList();

        return hoursRepo.saveAll(entities).stream()
                .sorted(Comparator.comparingInt(OpeningHours::getDayOfWeek)
                        .thenComparing(OpeningHours::getOpenTime))
                .map(OpeningSlotResponse::from).toList();
    }

    /** Regles metier. Package-private pour pouvoir la tester sans base de donnees. */
    static void validate(List<OpeningSlot> slots) {
        for (OpeningSlot s : slots) {
            if (!s.closeTime().isAfter(s.openTime())) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Jour " + s.dayOfWeek() + " : l'heure de fermeture doit etre apres l'ouverture");
            }
        }
        for (int day = 1; day <= 7; day++) {
            final int d = day;
            List<OpeningSlot> sameDay = slots.stream()
                    .filter(s -> s.dayOfWeek() == d)
                    .sorted(Comparator.comparing(OpeningSlot::openTime))
                    .toList();
            for (int i = 1; i < sameDay.size(); i++) {
                if (sameDay.get(i).openTime().isBefore(sameDay.get(i - 1).closeTime())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST,
                            "Jour " + d + " : des plages horaires se chevauchent");
                }
            }
        }
    }

    // ---------- Jours de fermeture ----------

    @Transactional(readOnly = true)
    public List<ClosedDayResponse> listClosedDays(UUID ownerId, UUID fieldId) {
        fieldService.getOwned(ownerId, fieldId);
        return closedRepo.findByFieldIdOrderByDayAsc(fieldId)
                .stream().map(ClosedDayResponse::from).toList();
    }

    @Transactional
    public ClosedDayResponse addClosedDay(UUID ownerId, UUID fieldId, ClosedDayRequest req) {
        fieldService.getOwned(ownerId, fieldId);
        if (closedRepo.existsByFieldIdAndDay(fieldId, req.day())) {
            throw new ApiException(HttpStatus.CONFLICT, "Ce jour est deja marque comme ferme");
        }
        ClosedDay c = new ClosedDay();
        c.setFieldId(fieldId);
        c.setDay(req.day());
        c.setReason(req.reason());
        return ClosedDayResponse.from(closedRepo.save(c));
    }

    @Transactional
    public void deleteClosedDay(UUID ownerId, UUID fieldId, UUID closedDayId) {
        fieldService.getOwned(ownerId, fieldId);
        ClosedDay c = closedRepo.findByIdAndFieldId(closedDayId, fieldId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Jour de fermeture introuvable"));
        closedRepo.delete(c);
    }
}