package com.footbooking.admin;

import com.footbooking.venue.VenueDtos.VenueResponse;
import com.footbooking.venue.VenueService;
import com.footbooking.venue.VenueStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/venues")
@PreAuthorize("hasRole('ADMIN')")
public class AdminVenueController {

    private final VenueService venueService;

    public AdminVenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    /** Par defaut : les complexes en attente de validation. */
    @GetMapping
    public List<VenueResponse> list(@RequestParam(defaultValue = "PENDING") VenueStatus status) {
        return venueService.listByStatus(status);
    }

    @PostMapping("/{venueId}/approve")
    public VenueResponse approve(@PathVariable UUID venueId) {
        return venueService.approve(venueId);
    }

    @PostMapping("/{venueId}/suspend")
    public VenueResponse suspend(@PathVariable UUID venueId) {
        return venueService.suspend(venueId);
    }
}