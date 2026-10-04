package com.footbooking.availability;

import com.footbooking.availability.AvailabilityDtos.AvailabilityResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/fields")
public class AvailabilityController {

    private final AvailabilityService service;

    public AvailabilityController(AvailabilityService service) {
        this.service = service;
    }

    /** Public : pas besoin d'etre connecte pour consulter les creneaux. */
    @GetMapping("/{fieldId}/availability")
    public AvailabilityResponse availability(
            @PathVariable UUID fieldId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.forDay(fieldId, date);
    }
}