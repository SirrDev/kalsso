package com.footbooking.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserContactLookup {

    private final UserRepository users;

    public UserContactLookup(UserRepository users) {
        this.users = users;
    }

    /** Vide si l'utilisateur n'existe pas. Le telephone peut etre null ou vide. */
    @Transactional(readOnly = true)
    public Optional<UserContact> find(UUID userId) {
        return users.findById(userId)
                .map(u -> new UserContact(u.getPhone(), u.isWhatsappOptIn()));
    }
}