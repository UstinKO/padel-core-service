package com.padle.core.application.identity;

import java.io.Serializable;
import java.util.Locale;

public record PlayerIdentity(Long id, String email) implements Serializable {

    private static final String GUEST_EMAIL_SUFFIX = "@1padel.guest";

    public boolean isGuestAccount() {
        return isGuestEmail(email);
    }

    public static boolean isGuestEmail(String email) {
        return email != null && email.toLowerCase(Locale.ROOT).endsWith(GUEST_EMAIL_SUFFIX);
    }
}
