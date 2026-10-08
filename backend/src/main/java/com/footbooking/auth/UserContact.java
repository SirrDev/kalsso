package com.footbooking.auth;

/** Ce dont un autre domaine a besoin pour joindre un utilisateur. Jamais journalise. */
public record UserContact(String phone, boolean whatsappOptIn) {
}