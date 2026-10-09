package com.footbooking.auth;

import com.footbooking.auth.AdminBootstrap.Result;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapTest {

    private final UserRepository users = Mockito.mock(UserRepository.class);
    private final PasswordEncoder encoder = Mockito.mock(PasswordEncoder.class);

    private AdminBootstrap with(String email, String password) {
        when(encoder.encode(any())).thenReturn("HASH");
        return new AdminBootstrap(users, encoder, email, password);
    }

    @Test
    void noVariables_doesNothing() {
        assertEquals(Result.DISABLED, with("", "").bootstrap());
        verify(users, never()).save(any());
    }

    @Test
    void createsAnAdminWithAHashedPassword() {
        when(users.existsByEmailIgnoreCase("Admin@Example.com")).thenReturn(false);

        assertEquals(Result.CREATED, with("Admin@Example.com", "un-mot-de-passe-long").bootstrap());

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(users).save(cap.capture());
        assertEquals(Role.ADMIN, cap.getValue().getRole());
        assertEquals("admin@example.com", cap.getValue().getEmail());
        assertEquals("HASH", cap.getValue().getPasswordHash());
    }

    @Test
    void existingAccount_isNeverModified() {
        when(users.existsByEmailIgnoreCase("admin@example.com")).thenReturn(true);

        assertEquals(Result.ALREADY_PRESENT, with("admin@example.com", "un-mot-de-passe-long").bootstrap());
        verify(users, never()).save(any());
    }

    @Test
    void shortPassword_failsTheStartup() {
        assertThrows(IllegalStateException.class, () -> with("admin@example.com", "court").bootstrap());
        verify(users, never()).save(any());
    }

    @Test
    void onlyOneVariable_failsTheStartup() {
        assertThrows(IllegalStateException.class, () -> with("admin@example.com", "").bootstrap());
        assertThrows(IllegalStateException.class, () -> with("", "un-mot-de-passe-long").bootstrap());
    }
}