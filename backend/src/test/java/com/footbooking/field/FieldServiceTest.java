package com.footbooking.field;

import com.footbooking.common.ApiException;
import com.footbooking.field.FieldDtos.FieldRequest;
import com.footbooking.venue.Venue;
import com.footbooking.venue.VenueService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FieldServiceTest {

    private final FieldRepository repo = Mockito.mock(FieldRepository.class);
    private final VenueService venueService = Mockito.mock(VenueService.class);
    private final FieldService service = new FieldService(repo, venueService);

    private final UUID owner = UUID.randomUUID();
    private final UUID venueId = UUID.randomUUID();

    @Test
    void create_invalidSlotMinutes_returns400() {
        when(venueService.getOwned(owner, venueId)).thenReturn(new Venue());
        FieldRequest req = new FieldRequest("Terrain 1", FieldFormat.FIVE, FieldSurface.SYNTHETIC, 45);

        ApiException ex = assertThrows(ApiException.class, () -> service.create(owner, venueId, req));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(repo, never()).save(any());
    }

    @Test
    void create_foreignVenue_propagates404() {
        when(venueService.getOwned(owner, venueId))
                .thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Complexe introuvable"));
        FieldRequest req = new FieldRequest("Terrain 1", FieldFormat.FIVE, FieldSurface.SYNTHETIC, 60);

        ApiException ex = assertThrows(ApiException.class, () -> service.create(owner, venueId, req));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        verify(repo, never()).save(any());
    }

    @Test
    void create_valid_savesField() {
        when(venueService.getOwned(owner, venueId)).thenReturn(new Venue());
        when(repo.save(any(Field.class))).thenAnswer(inv -> inv.getArgument(0));
        FieldRequest req = new FieldRequest("Terrain 1", FieldFormat.SEVEN, FieldSurface.NATURAL, 90);

        var res = service.create(owner, venueId, req);

        assertEquals(90, res.slotMinutes());
        assertEquals(FieldFormat.SEVEN, res.format());
        assertTrue(res.active());
    }
}
