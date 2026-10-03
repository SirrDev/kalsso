package com.footbooking.field;

import com.footbooking.common.CurrentUser;
import com.footbooking.field.OpeningHoursDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/owner/fields/{fieldId}")
@PreAuthorize("hasRole('OWNER')")
public class OpeningHoursController {

    private final OpeningHoursService service;

    public OpeningHoursController(OpeningHoursService service) {
        this.service = service;
    }

    @GetMapping("/opening-hours")
    public List<OpeningSlotResponse> getWeek(Authentication auth, @PathVariable UUID fieldId) {
        return service.getWeek(CurrentUser.id(auth), fieldId);
    }

    @PutMapping("/opening-hours")
    public List<OpeningSlotResponse> replaceWeek(Authentication auth, @PathVariable UUID fieldId,
                                                 @Valid @RequestBody OpeningHoursRequest req) {
        return service.replaceWeek(CurrentUser.id(auth), fieldId, req.slots());
    }

    @GetMapping("/closed-days")
    public List<ClosedDayResponse> listClosedDays(Authentication auth, @PathVariable UUID fieldId) {
        return service.listClosedDays(CurrentUser.id(auth), fieldId);
    }

    @PostMapping("/closed-days")
    @ResponseStatus(HttpStatus.CREATED)
    public ClosedDayResponse addClosedDay(Authentication auth, @PathVariable UUID fieldId,
                                          @Valid @RequestBody ClosedDayRequest req) {
        return service.addClosedDay(CurrentUser.id(auth), fieldId, req);
    }

    @DeleteMapping("/closed-days/{closedDayId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteClosedDay(Authentication auth, @PathVariable UUID fieldId,
                                @PathVariable UUID closedDayId) {
        service.deleteClosedDay(CurrentUser.id(auth), fieldId, closedDayId);
    }
}