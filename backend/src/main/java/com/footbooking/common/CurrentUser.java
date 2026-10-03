package com.footbooking.common;

import org.springframework.security.core.Authentication;
import java.util.UUID;

public final class CurrentUser {
    private CurrentUser() {}

    /** L'id utilisateur est le "subject" du JWT. */
    public static UUID id(Authentication auth) {
        return UUID.fromString(auth.getName());
    }
}
