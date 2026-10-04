package com.footbooking.booking;

import com.footbooking.booking.OwnerBookingDtos.BlockRequest;
import com.footbooking.booking.OwnerBookingDtos.OwnerBookingResponse;
import com.footbooking.booking.OwnerBookingService.DateRange;
import com.footbooking.common.ApiException;
import com.footbooking.field.Field;
import com.footbooking.field.FieldService;
import com.footbooking.venue.VenueService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OwnerBookingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final Instant START = Instant.parse("2026-10-10T19:00:00Z");
    private static final Instant END = Instant.parse("2026-10-10T20:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);

    private final BookingRepository repo = Mockito.mock(BookingRepository.class);
    private final FieldService fieldService = Mockito.mock(FieldService.class);
    private final VenueService venueService = Mockito.mock(VenueService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final OwnerBookingService service =
            new OwnerBookingService(repo, fieldService, venueService, clock);

    private final UUID ownerId = UUID.randomUUID();
    private final UUID fieldId = UUID.randomUUID();

    private static ApiException thrown(Runnable r) {
        return assertThrows(ApiException.class, r::run);
    }

    // ---------- resolveRange ----------

    @Test
    void resolveRange_defaultsToTodayPlusThirtyDays() {
        DateRange r = OwnerBookingService.resolveRange(null, null, TODAY);

        assertEquals(TODAY, r.from());
        assertEquals(TODAY.plusDays(30), r.to());
    }

    @Test
    void resolveRange_toBeforeFrom_returns400() {
        ApiException ex = thrown(() ->
                OwnerBookingService.resolveRange(TODAY.plusDays(5), TODAY, TODAY));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void resolveRange_tooLong_returns400() {
        ApiException ex = thrown(() ->
                OwnerBookingService.resolveRange(TODAY, TODAY.plusDays(63), TODAY));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    // ---------- validateBlock ----------

    @Test
    void validateBlock_endBeforeStart_returns400() {
        ApiException ex = thrown(() -> OwnerBookingService.validateBlock(END, START, NOW));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void validateBlock_tooLong_returns400() {
        ApiException ex = thrown(() ->
                OwnerBookingService.validateBlock(START, START.plusSeconds(15 * 24 * 3600L), NOW));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void validateBlock_entirelyInThePast_returns400() {
        ApiException ex = thrown(() -> OwnerBookingService.validateBlock(
                Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-01T11:00:00Z"), NOW));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void validateBlock_valid_isAccepted() {
        assertDoesNotThrow(() -> OwnerBookingService.validateBlock(START, END, NOW));
    }

    // ---------- block / unblock ----------

    @Test
    void block_valid_isConfirmedBlockWithoutClient() {
        when(fieldService.getOwned(ownerId, fieldId)).thenReturn(new Field());
        when(repo.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        OwnerBookingResponse res = service.block(ownerId, fieldId, new BlockRequest(START, END, "Maintenance"));

        assertEquals(BookingStatus.CONFIRMED, res.status());
        assertEquals(BookingSource.BLOCK, res.source());
        assertNull(res.userId());
        assertEquals("Maintenance", res.note());
    }

    @Test
    void block_overlappingExistingBooking_returns409() {
        when(fieldService.getOwned(ownerId, fieldId)).thenReturn(new Field());
        when(repo.saveAndFlush(any(Booking.class))).thenThrow(new DataIntegrityViolationException(
                "could not execute statement", new RuntimeException("violates exclusion constraint \"booking_no_overlap\"")));

        ApiException ex = thrown(() -> service.block(ownerId, fieldId, new BlockRequest(START, END, null)));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void block_foreignField_returns404AndWritesNothing() {
        when(fieldService.getOwned(ownerId, fieldId))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Terrain introuvable"));

        ApiException ex = thrown(() -> service.block(ownerId, fieldId, new BlockRequest(START, END, null)));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(repo, never()).saveAndFlush(any());
    }

    private Booking existing(BookingSource source, BookingStatus status, UUID onField) {
        Booking b = new Booking();
        b.setFieldId(onField);
        b.setSource(source);
        b.setStatus(status);
        b.setStartTime(START);
        b.setEndTime(END);
        return b;
    }

    @Test
    void unblock_ownBlock_isCancelled() {
        UUID id = UUID.randomUUID();
        Booking b = existing(BookingSource.BLOCK, BookingStatus.CONFIRMED, fieldId);
        when(fieldService.getOwned(ownerId, fieldId)).thenReturn(new Field());
        when(repo.findById(id)).thenReturn(Optional.of(b));

        service.unblock(ownerId, fieldId, id);

        assertEquals(BookingStatus.CANCELLED, b.getStatus());
    }

    @Test
    void unblock_aClientBooking_returns404() {
        UUID id = UUID.randomUUID();
        when(fieldService.getOwned(ownerId, fieldId)).thenReturn(new Field());
        when(repo.findById(id)).thenReturn(Optional.of(
                existing(BookingSource.ONLINE, BookingStatus.CONFIRMED, fieldId)));

        ApiException ex = thrown(() -> service.unblock(ownerId, fieldId, id));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void unblock_blockOfAnotherField_returns404() {
        UUID id = UUID.randomUUID();
        when(fieldService.getOwned(ownerId, fieldId)).thenReturn(new Field());
        when(repo.findById(id)).thenReturn(Optional.of(
                existing(BookingSource.BLOCK, BookingStatus.CONFIRMED, UUID.randomUUID())));

        ApiException ex = thrown(() -> service.unblock(ownerId, fieldId, id));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}