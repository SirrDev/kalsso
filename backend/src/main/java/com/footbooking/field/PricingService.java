package com.footbooking.field;

import com.footbooking.common.ApiException;
import com.footbooking.field.PricingDtos.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.function.Function;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class PricingService {

    private final PricingRuleRepository repo;
    private final FieldService fieldService;

    public PricingService(PricingRuleRepository repo, FieldService fieldService) {
        this.repo = repo;
        this.fieldService = fieldService;
    }

    // ---------- Gestion (cote proprietaire) ----------

    @Transactional(readOnly = true)
    public List<PricingRuleResponse> list(UUID ownerId, UUID fieldId) {
        fieldService.getOwned(ownerId, fieldId);
        return repo.findByFieldIdOrderByDayOfWeekAscStartTimeAsc(fieldId)
                .stream().map(PricingRuleResponse::from).toList();
    }

    @Transactional
    public PricingRuleResponse add(UUID ownerId, UUID fieldId, PricingRuleRequest req) {
        fieldService.getOwned(ownerId, fieldId);
        String currency = req.currency() == null ? "XOF" : req.currency();
        List<PricingRule> existing = repo.findByFieldIdOrderByDayOfWeekAscStartTimeAsc(fieldId);
        validate(req.dayOfWeek(), req.startTime(), req.endTime(), currency, existing);

        PricingRule r = new PricingRule();
        r.setFieldId(fieldId);
        r.setDayOfWeek(req.dayOfWeek());
        r.setStartTime(req.startTime());
        r.setEndTime(req.endTime());
        r.setPrice(req.price());
        r.setCurrency(currency);
        return PricingRuleResponse.from(repo.save(r));
    }

    @Transactional
    public void delete(UUID ownerId, UUID fieldId, UUID ruleId) {
        fieldService.getOwned(ownerId, fieldId);
        PricingRule r = repo.findByIdAndFieldId(ruleId, fieldId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Regle de prix introuvable"));
        repo.delete(r);
    }

    // ---------- Calcul du prix (utilise par B3 et B4) ----------

    /**
     * Prix d'un creneau = regle en vigueur a son HEURE DE DEBUT (choix A).
     * Pas de controle de propriete : cette methode est appelee par les parcours client.
     * @param start date et heure locales du creneau, dans le fuseau du complexe
     */
    @Transactional(readOnly = true)
    public BigDecimal priceFor(UUID fieldId, LocalDateTime start) {
        List<PricingRule> rules = repo.findByFieldIdOrderByDayOfWeekAscStartTimeAsc(fieldId);
        return resolve(rules, start.getDayOfWeek().getValue(), start.toLocalTime())
                .map(PricingRule::getPrice)
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Aucun tarif defini pour ce creneau"));
    }

    public record Price(BigDecimal amount, String currency) {}

    /**
     * Charge les regles UNE fois et renvoie une fonction "heure de debut -> prix".
     * Evite une requete SQL par creneau quand on calcule une journee entiere.
     * Optional vide = aucun tarif pour cette heure (creneau non reservable).
     */
    @Transactional(readOnly = true)
    public Function<LocalDateTime, Optional<Price>> priceResolver(UUID fieldId) {
        List<PricingRule> rules = repo.findByFieldIdOrderByDayOfWeekAscStartTimeAsc(fieldId);
        return start -> resolve(rules, start.getDayOfWeek().getValue(), start.toLocalTime())
                .map(r -> new Price(r.getPrice(), r.getCurrency()));
    }

    // ---------- Regles pures (testables sans base de donnees) ----------

    /**
     * Parmi les regles qui couvrent l'heure demandee (debut inclus, fin exclue),
     * celle qui vise un jour precis l'emporte sur celle de "tous les jours".
     */
    static Optional<PricingRule> resolve(List<PricingRule> rules, int dayOfWeek, LocalTime time) {
        return rules.stream()
                .filter(r -> r.getDayOfWeek() == null || r.getDayOfWeek() == dayOfWeek)
                .filter(r -> !time.isBefore(r.getStartTime()) && time.isBefore(r.getEndTime()))
                .max(Comparator.comparingInt(r -> r.getDayOfWeek() == null ? 0 : 1));
    }

    static void validate(Integer dayOfWeek, LocalTime start, LocalTime end,
                         String currency, List<PricingRule> existing) {
        if (!end.isAfter(start)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "L'heure de fin doit etre apres l'heure de debut");
        }
        for (PricingRule r : existing) {
            if (!r.getCurrency().equals(currency)) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Toutes les regles d'un terrain doivent avoir la meme devise");
            }
            // Chevauchement interdit seulement entre regles de meme portee :
            // "samedi" peut recouvrir "tous les jours", c'est voulu (la plus precise gagne).
            boolean sameScope = Objects.equals(r.getDayOfWeek(), dayOfWeek);
            if (sameScope && start.isBefore(r.getEndTime()) && end.isAfter(r.getStartTime())) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Cette plage chevauche une regle existante de meme portee");
            }
        }
    }
}