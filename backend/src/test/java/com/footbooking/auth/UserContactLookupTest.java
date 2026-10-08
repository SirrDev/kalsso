package com.footbooking.auth;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class UserContactLookupTest {

    private final UserRepository users = Mockito.mock(UserRepository.class);
    private final UserContactLookup lookup = new UserContactLookup(users);
    private final UUID id = UUID.randomUUID();

    @Test
    void returnsPhoneAndConsent() {
        User u = new User();
        u.setPhone("+221700000000");
        u.setWhatsappOptIn(true);
        when(users.findById(id)).thenReturn(Optional.of(u));

        UserContact c = lookup.find(id).orElseThrow();

        assertEquals("+221700000000", c.phone());
        assertTrue(c.whatsappOptIn());
    }

    @Test
    void unknownUser_isEmpty() {
        when(users.findById(id)).thenReturn(Optional.empty());

        assertTrue(lookup.find(id).isEmpty());
    }

    @Test
    void missingPhone_isKeptAsNull() {
        when(users.findById(id)).thenReturn(Optional.of(new User()));

        UserContact c = lookup.find(id).orElseThrow();

        assertNull(c.phone());
        assertFalse(c.whatsappOptIn());
    }
}