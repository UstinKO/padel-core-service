package com.padle.core.application.identity;

import com.padle.core.application.tournament.Nivel;

import java.time.LocalDateTime;
import java.util.Optional;

public interface PlayerProfiles {

    Optional<Profile> findById(Long id);

    Optional<Login> findForLogin(String email);

    boolean existsActiveByIdentity(PlayerIdentity identity);

    boolean existsByEmail(String email);

    boolean existsByTelegramUsername(String username);

    Profile update(Long id, ProfileUpdate update);

    boolean updateActiveLevel(Long id, Nivel nivel, LocalDateTime updatedAt);

    record Profile(Long id, String nombre, String apellido, String email, String telefono,
                   String telegramUsername, Nivel nivelJugador, String preferredLocale,
                   boolean oauth2User, PlayingSide playingSide, boolean activo, boolean emailConfirmado) {
        public boolean isGuestAccount() {
            return PlayerIdentity.isGuestEmail(email);
        }

        public boolean hasValidContact() {
            return PlayerContacts.hasValidContact(telefono, telegramUsername);
        }

        public String getNombreCompleto() {
            return nombre + " " + apellido;
        }
    }

    record ProfileUpdate(String nombre, String apellido, String email, String telefono,
                         String telegramUsername, Nivel nivelJugador, String preferredLocale) {}

    record Login(Profile profile, String passwordHash) {
        @Override
        public String toString() {
            return "Login[playerId=" + profile.id() + "]";
        }
    }
}
