package com.footbooking.auth;

import jakarta.validation.constraints.*;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 150) String fullName,
            @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Numero de telephone invalide") String phone,
            @NotNull Role role,
            boolean whatsappOptIn) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {}

    public record UserResponse(UUID id, String email, String fullName, String phone, Role role) {
        static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getRole());
        }
    }

    public record AuthResponse(String token, String refreshToken, UserResponse user) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}
}
