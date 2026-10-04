package com.footbooking.venue;

import com.footbooking.common.ApiException;
import com.footbooking.venue.VenueDtos.VenueRequest;
import com.footbooking.venue.VenueDtos.VenueResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneId;

import java.util.List;
import java.util.UUID;

@Service
public class VenueService {

    private final VenueRepository venues;

    public VenueService(VenueRepository venues) {
        this.venues = venues;
    }

    @Transactional
    public VenueResponse create(UUID ownerId, VenueRequest req) {
        Venue v = new Venue();
        v.setOwnerId(ownerId);
        v.setStatus(VenueStatus.PENDING); // validation par l'admin (B7)
        apply(v, req);
        return VenueResponse.from(venues.save(v));
    }

    @Transactional(readOnly = true)
    public List<VenueResponse> listMine(UUID ownerId) {
        return venues.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(VenueResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public VenueResponse get(UUID ownerId, UUID venueId) {
        return VenueResponse.from(getOwned(ownerId, venueId));
    }

    @Transactional
    public VenueResponse update(UUID ownerId, UUID venueId, VenueRequest req) {
        Venue v = getOwned(ownerId, venueId);
        apply(v, req);
        return VenueResponse.from(v);
    }

    @Transactional
    public void delete(UUID ownerId, UUID venueId) {
        Venue v = getOwned(ownerId, venueId);
        try {
            venues.delete(v);
            venues.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Suppression impossible : des reservations existent pour ce complexe");
        }
    }

    /**
     * Point d'entree du controle d'acces : renvoie 404 (et non 403) si le complexe
     * n'appartient pas a l'utilisateur, pour ne pas reveler son existence.
     * Reutilise par les autres domaines (field, availability...).
     */
    @Transactional(readOnly = true)
    public Venue getOwned(UUID ownerId, UUID venueId) {
        return venues.findById(venueId)
                .filter(v -> v.getOwnerId().equals(ownerId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complexe introuvable"));
    }

    /** Lecture sans controle de propriete, pour les parcours publics (disponibilites, recherche). */
    @Transactional(readOnly = true)
    public Venue getById(UUID venueId) {
        return venues.findById(venueId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Complexe introuvable"));
    }

    private void apply(Venue v, VenueRequest req) {
        v.setName(req.name().trim());
        v.setDescription(req.description());
        v.setAddress(req.address().trim());
        v.setCity(req.city().trim());
        v.setLatitude(req.latitude());
        v.setLongitude(req.longitude());
        v.setPhone(req.phone());
        if (req.timezone() != null && !req.timezone().isBlank()) {
            v.setTimezone(validTimezone(req.timezone().trim()));
        }
    }

    static String validTimezone(String tz) {
        if (!ZoneId.getAvailableZoneIds().contains(tz)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Fuseau horaire invalide (exemple : Africa/Dakar)");
        }
        return tz;
    }
}
