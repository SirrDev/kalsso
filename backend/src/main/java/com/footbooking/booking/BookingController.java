package com.footbooking.booking;

import com.footbooking.booking.BookingDtos.BookingResponse;
import com.footbooking.booking.BookingDtos.CreateBookingRequest;
import com.footbooking.common.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
@PreAuthorize("hasRole('CLIENT')")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(Authentication auth, @Valid @RequestBody CreateBookingRequest req) {
        return service.create(CurrentUser.id(auth), req.fieldId(), req.start());
    }
}