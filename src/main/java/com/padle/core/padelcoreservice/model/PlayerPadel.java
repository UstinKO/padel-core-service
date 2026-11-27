package com.padle.core.padelcoreservice.model;

import com.padle.core.application.identity.PlayerContacts;
import com.padle.core.application.identity.PlayerIdentity;
import com.padle.core.padelcoreservice.security.PlayerPrincipal;

import java.io.Serial;
import java.io.Serializable;

/** Формат сессий, записанных до отделения principal игрока. */
public final class PlayerPadel implements Serializable {

    @Serial
    private static final long serialVersionUID = 3954731546753820827L;

    private Long id;
    private String nombre;
    private String apellido;
    private String email;
    private String telefono;
    private String telegramUsername;
    private String passwordHash;
    private boolean activo;
    private boolean emailConfirmado;
    private String preferredLocale;

    private PlayerPadel() {}

    public PlayerPrincipal toPrincipal() {
        boolean hasContact = PlayerContacts.hasValidContact(telefono, telegramUsername);
        return new PlayerPrincipal(new PlayerIdentity(id, email), nombre, apellido, passwordHash,
                activo, emailConfirmado, hasContact, preferredLocale);
    }
}
