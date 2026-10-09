package com.footbooking.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Cree le compte ADMIN au demarrage depuis l'environnement. Ne modifie jamais un compte existant. */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    static final int MIN_PASSWORD_LENGTH = 12;

    public enum Result { DISABLED, CREATED, ALREADY_PRESENT }

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository users, PasswordEncoder encoder,
                          @Value("${app.admin.email:}") String email,
                          @Value("${app.admin.password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.email = email == null ? "" : email.trim();
        this.password = password == null ? "" : password;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Jamais l'email ni le mot de passe dans les logs
        switch (bootstrap()) {
            case CREATED -> log.info("Compte ADMIN cree");
            case ALREADY_PRESENT -> log.info("Compte ADMIN deja present : aucune modification");
            case DISABLED -> { }
        }
    }

    @Transactional
    public Result bootstrap() {
        if (email.isEmpty() && password.isEmpty()) {
            return Result.DISABLED;
        }
        if (email.isEmpty() || password.isEmpty()) {
            throw new IllegalStateException("app.admin.email et app.admin.password doivent etre definis ensemble");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "app.admin.password doit contenir au moins " + MIN_PASSWORD_LENGTH + " caracteres");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            return Result.ALREADY_PRESENT;
        }
        User u = new User();
        u.setEmail(email.toLowerCase());
        u.setPasswordHash(encoder.encode(password));
        u.setFullName("Administrateur");
        u.setRole(Role.ADMIN);
        users.save(u);
        return Result.CREATED;
    }
}