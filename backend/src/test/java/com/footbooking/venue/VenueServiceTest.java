package com.footbooking.venue;

import com.footbooking.common.ApiException;
import com.footbooking.venue.VenueDtos.VenueRequest;
import com.footbooking.venue.VenueDtos.VenueResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class VenueServiceTest {

    private final VenueRepository repo = Mockito.mock(VenueRepository.class);
    private final VenueService service = new VenueService(repo);

    private final UUID ownerA = UUID.randomUUID();
    private final UUID ownerB = UUID.randomUUID();

    private VenueRequest request() {
        return new VenueRequest("Stade Pikine", null, "Rue 10", "Dakar", null, null, "+221770000000");
    }

    @Test
    void create_setsPendingStatusAndOwner() {
        when(repo.save(any(Venue.class))).thenAnswer(inv -> inv.getArgument(0));

        VenueResponse res = service.create(ownerA, request());

        assertEquals(VenueStatus.PENDING, res.status());
        assertEquals("Stade Pikine", res.name());
    }

    @Test
    void getOwned_otherOwner_returns404() {
        Venue venue = new Venue();
        venue.setOwnerId(ownerA);
        UUID venueId = UUID.randomUUID();
        when(repo.findById(venueId)).thenReturn(Optional.of(venue));

        ApiException ex = assertThrows(ApiException.class, () -> service.getOwned(ownerB, venueId));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void getOwned_sameOwner_returnsVenue() {
        Venue venue = new Venue();
        venue.setOwnerId(ownerA);
        UUID venueId = UUID.randomUUID();
        when(repo.findById(venueId)).thenReturn(Optional.of(venue));

        assertSame(venue, service.getOwned(ownerA, venueId));
    }
}
