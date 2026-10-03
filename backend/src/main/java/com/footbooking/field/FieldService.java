package com.footbooking.field;

import com.footbooking.common.ApiException;
import com.footbooking.field.FieldDtos.FieldRequest;
import com.footbooking.field.FieldDtos.FieldResponse;
import com.footbooking.venue.VenueService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class FieldService {

    static final Set<Integer> ALLOWED_SLOT_MINUTES = Set.of(30, 60, 90, 120);

    private final FieldRepository fields;
    private final VenueService venueService;

    public FieldService(FieldRepository fields, VenueService venueService) {
        this.fields = fields;
        this.venueService = venueService;
    }

    @Transactional
    public FieldResponse create(UUID ownerId, UUID venueId, FieldRequest req) {
        venueService.getOwned(ownerId, venueId); // 404 si le complexe n'est pas a lui
        validateSlot(req.slotMinutes());
        Field f = new Field();
        f.setVenueId(venueId);
        apply(f, req);
        return FieldResponse.from(fields.save(f));
    }

    @Transactional(readOnly = true)
    public List<FieldResponse> listByVenue(UUID ownerId, UUID venueId) {
        venueService.getOwned(ownerId, venueId);
        return fields.findByVenueIdOrderByNameAsc(venueId).stream().map(FieldResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public FieldResponse get(UUID ownerId, UUID fieldId) {
        return FieldResponse.from(getOwned(ownerId, fieldId));
    }

    @Transactional
    public FieldResponse update(UUID ownerId, UUID fieldId, FieldRequest req) {
        Field f = getOwned(ownerId, fieldId);
        validateSlot(req.slotMinutes());
        apply(f, req);
        return FieldResponse.from(f);
    }

    /** Desactivation (suppression douce) : l'historique des reservations est conserve. */
    @Transactional
    public void deactivate(UUID ownerId, UUID fieldId) {
        getOwned(ownerId, fieldId).setActive(false);
    }

    /** Verifie la propriete via le complexe. Reutilise par horaires, tarifs, reservations. */
    @Transactional(readOnly = true)
    public Field getOwned(UUID ownerId, UUID fieldId) {
        Field f = fields.findById(fieldId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Terrain introuvable"));
        venueService.getOwned(ownerId, f.getVenueId());
        return f;
    }

    private void validateSlot(Integer minutes) {
        if (minutes == null || !ALLOWED_SLOT_MINUTES.contains(minutes)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Duree de creneau invalide (valeurs autorisees : 30, 60, 90, 120)");
        }
    }

    private void apply(Field f, FieldRequest req) {
        f.setName(req.name().trim());
        f.setFormat(req.format());
        f.setSurface(req.surface());
        f.setSlotMinutes(req.slotMinutes());
    }
}
