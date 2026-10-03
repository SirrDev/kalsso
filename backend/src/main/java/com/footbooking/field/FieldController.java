package com.footbooking.field;

import com.footbooking.common.CurrentUser;
import com.footbooking.field.FieldDtos.FieldRequest;
import com.footbooking.field.FieldDtos.FieldResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/owner")
@PreAuthorize("hasRole('OWNER')")
public class FieldController {

    private final FieldService service;

    public FieldController(FieldService service) {
        this.service = service;
    }

    @PostMapping("/venues/{venueId}/fields")
    @ResponseStatus(HttpStatus.CREATED)
    public FieldResponse create(Authentication auth, @PathVariable UUID venueId,
                                @Valid @RequestBody FieldRequest req) {
        return service.create(CurrentUser.id(auth), venueId, req);
    }

    @GetMapping("/venues/{venueId}/fields")
    public List<FieldResponse> list(Authentication auth, @PathVariable UUID venueId) {
        return service.listByVenue(CurrentUser.id(auth), venueId);
    }

    @GetMapping("/fields/{fieldId}")
    public FieldResponse get(Authentication auth, @PathVariable UUID fieldId) {
        return service.get(CurrentUser.id(auth), fieldId);
    }

    @PutMapping("/fields/{fieldId}")
    public FieldResponse update(Authentication auth, @PathVariable UUID fieldId,
                                @Valid @RequestBody FieldRequest req) {
        return service.update(CurrentUser.id(auth), fieldId, req);
    }

    @DeleteMapping("/fields/{fieldId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(Authentication auth, @PathVariable UUID fieldId) {
        service.deactivate(CurrentUser.id(auth), fieldId);
    }
}
