package com.footbooking.auth;

import com.footbooking.auth.AuthDtos.*;
import com.footbooking.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        // L'auto-inscription est limitee aux clients et proprietaires
        if (req.role() != Role.CLIENT && req.role() != Role.OWNER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Role non autorise a l'inscription");
        }
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw new ApiException(HttpStatus.CONFLICT, "Cet email est deja utilise");
        }
        User u = new User();
        u.setEmail(req.email().trim().toLowerCase());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setFullName(req.fullName().trim());
        u.setPhone(req.phone());
        u.setRole(req.role());
        u.setWhatsappOptIn(req.whatsappOptIn());
        users.save(u);
        return new AuthResponse(jwt.generate(u), UserResponse.from(u));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User u = users.findByEmailIgnoreCase(req.email())
                .filter(User::isEnabled)
                .filter(x -> encoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Email ou mot de passe incorrect"));
        return new AuthResponse(jwt.generate(u), UserResponse.from(u));
    }
}
