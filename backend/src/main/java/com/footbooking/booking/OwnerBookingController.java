package com.footbooking.booking;

import com.footbooking.booking.OwnerBookingDtos.BlockRequest;
import com.footbooking.booking.OwnerBookingDtos.OwnerBookingResponse;
import com.footbooking.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/owner/fields/{fieldId}")
@PreAuthorize("hasRole('OWNER')")
public class OwnerBookingController {

    private final OwnerBookingService service;

    public OwnerBookingController(OwnerBookingService service) {
        this.service = service;
    }

    @GetMapping("/bookings")
    public List<OwnerBookingResponse> list(
            Authentication auth, @PathVariable UUID fieldId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(CurrentUser.id(auth), fieldId, from, to);
    }

    @PostMapping("/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    public OwnerBookingResponse block(Authentication auth, @PathVariable UUID fieldId,
                                      @Valid @RequestBody BlockRequest req) {
        return service.block(CurrentUser.id(auth), fieldId, req);
    }

    @DeleteMapping("/blocks/{bookingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(Authentication auth, @PathVariable UUID fieldId, @PathVariable UUID bookingId) {
        service.unblock(CurrentUser.id(auth), fieldId, bookingId);
    }
}