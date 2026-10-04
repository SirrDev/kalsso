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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;
import java.util.UUID;

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

    @GetMapping("/mine")
    public List<BookingResponse> mine(Authentication auth) {
        return service.listMine(CurrentUser.id(auth));
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancel(Authentication auth, @PathVariable UUID bookingId) {
        return service.cancel(CurrentUser.id(auth), bookingId);
    }
}