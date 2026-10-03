package com.footbooking.venue;

import com.footbooking.common.CurrentUser;
import com.footbooking.venue.VenueDtos.VenueRequest;
import com.footbooking.venue.VenueDtos.VenueResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/owner/venues")
@PreAuthorize("hasRole('OWNER')")
public class VenueController {

    private final VenueService service;

    public VenueController(VenueService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VenueResponse create(Authentication auth, @Valid @RequestBody VenueRequest req) {
        return service.create(CurrentUser.id(auth), req);
    }

    @GetMapping
    public List<VenueResponse> list(Authentication auth) {
        return service.listMine(CurrentUser.id(auth));
    }

    @GetMapping("/{id}")
    public VenueResponse get(Authentication auth, @PathVariable UUID id) {
        return service.get(CurrentUser.id(auth), id);
    }

    @PutMapping("/{id}")
    public VenueResponse update(Authentication auth, @PathVariable UUID id, @Valid @RequestBody VenueRequest req) {
        return service.update(CurrentUser.id(auth), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable UUID id) {
        service.delete(CurrentUser.id(auth), id);
    }
}
