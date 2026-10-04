package com.footbooking.field;

import com.footbooking.common.CurrentUser;
import com.footbooking.field.PricingDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/owner/fields/{fieldId}/pricing")
@PreAuthorize("hasRole('OWNER')")
public class PricingController {

    private final PricingService service;

    public PricingController(PricingService service) {
        this.service = service;
    }

    @GetMapping
    public List<PricingRuleResponse> list(Authentication auth, @PathVariable UUID fieldId) {
        return service.list(CurrentUser.id(auth), fieldId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PricingRuleResponse add(Authentication auth, @PathVariable UUID fieldId,
                                   @Valid @RequestBody PricingRuleRequest req) {
        return service.add(CurrentUser.id(auth), fieldId, req);
    }

    @DeleteMapping("/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication auth, @PathVariable UUID fieldId, @PathVariable UUID ruleId) {
        service.delete(CurrentUser.id(auth), fieldId, ruleId);
    }
}