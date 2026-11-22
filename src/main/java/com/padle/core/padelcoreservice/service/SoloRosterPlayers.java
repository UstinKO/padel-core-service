package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.SoloRoster.PlayerInput;
import com.padle.core.padelcoreservice.exception.SoloRosterException;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SoloRosterPlayers {
    private final PlayerRepository players;
    private final PlayerService service;

    public void lock(PlayerInput input) {
        if (input == null || input.id() != null) return;
        var keys = new ArrayList<String>();
        if (input.phone() != null) players.claimPhoneKey(input.phone()).ifPresent(key -> keys.add("partner-phone:" + key));
        String username = telegram(input.telegram());
        if (!username.isBlank()) keys.add("roster-telegram:" + username.toLowerCase(Locale.ROOT));
        keys.stream().sorted().forEach(players::acquireContactLock);
    }

    public PlayerPadel resolve(PlayerInput input) {
        if (input == null) throw new SoloRosterException("solo.error.player");
        if (input.id() != null) {
            return players.findById(input.id()).filter(PlayerPadel::isActivo)
                    .orElseThrow(() -> new SoloRosterException("solo.error.player"));
        }
        if (blank(input.firstName()) || blank(input.lastName()) || blank(input.phone())
                || !input.phone().replaceAll("\\D", "").matches("[0-9]{7,15}")) {
            throw new SoloRosterException("solo.error.guest");
        }
        String telegram = telegram(input.telegram());
        if (!telegram.isBlank() && !telegram.matches("[A-Za-z][A-Za-z0-9_]{4,31}")) {
            throw new SoloRosterException("solo.error.guest");
        }
        List<PlayerPadel> matches = players.claimPhoneKey(input.phone())
                .map(players::findClaimPhoneMatches).orElseGet(List::of);
        var telegramMatches = telegram.isBlank() ? List.<PlayerPadel>of()
                : players.findAllByTelegramUsernameIgnoreCaseAndActivoTrue(telegram);
        Optional<PlayerPadel> byTelegram = telegramMatches.stream().findFirst();
        if (telegramMatches.size() > 1 || matches.size() > 1 || (byTelegram.isPresent() && !matches.isEmpty()
                && !byTelegram.orElseThrow().getId().equals(matches.get(0).getId()))) {
            throw new SoloRosterException("solo.error.contact");
        }
        return matches.stream().findFirst().or(() -> byTelegram).orElseGet(() -> PlayerPadel.builder()
                .nombre(input.firstName().strip()).apellido(input.lastName().strip())
                .telefono(input.phone().strip()).telegramUsername(telegram.isBlank() ? null : telegram)
                .nivelJugador(Nivel.SIN_ESPECIFICAR).activo(true).build());
    }

    public PlayerPadel materialize(PlayerPadel player) {
        if (player.getId() != null) return player;
        var guest = service.resolverJugadorPorTelefonoOCrearInvitado(player.getNombre(), player.getApellido(), player.getTelefono());
        guest.setTelegramUsername(player.getTelegramUsername());
        return players.save(guest);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private String telegram(String value) { return value == null ? "" : value.strip().replaceFirst("^@", ""); }
}
