package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"telegram.enabled=false", "telegram.players.enabled=false"})
@Transactional
class PlayerTelegramStoreIntegrationTest {
    @Autowired PlayerTelegramStore store;
    @Autowired PlayerRepository players;
    @Autowired JdbcTemplate jdbc;
    private PlayerPadel player() {
        return players.saveAndFlush(PlayerPadel.builder().nombre("Fixture").apellido("Telegram")
                .email(UUID.randomUUID()+"@gmail.com").passwordHash("fixture-hash")
                .nivelJugador(Nivel.C7).preferredLocale("es").activo(true).emailConfirmado(true).build());
    }
    @Test void tokenIsConsumedAndTelegramCannotBeStolenByAnotherProfile() {
        var first = player(); var second = player();
        store.token(first.getId(), "hash-one", OffsetDateTime.now().plusMinutes(10));
        assertThat(store.bind("hash-one", 45678, 45678)).contains(first.getId());
        assertThat(store.bind("hash-one", 45678, 45678)).isEmpty();
        store.token(second.getId(), "hash-two", OffsetDateTime.now().plusMinutes(10));
        assertThat(store.bind("hash-two", 45678, 45678)).isEmpty();
        assertThat(store.connection(first.getId())).isPresent();
        assertThat(store.connection(second.getId())).isEmpty();
        store.disconnect(first.getId());
        assertThat(store.byUser(45678)).isEmpty();
    }
    @Test void expiredLinkAndInactiveProfileCannotBind() {
        var player = player();
        store.token(player.getId(), "expired", OffsetDateTime.now().minusSeconds(1));
        assertThat(store.bind("expired", 56789, 56789)).isEmpty();
        jdbc.update("UPDATE player_padel_db SET activo=false WHERE id=?", player.getId());
        store.token(player.getId(), "inactive", OffsetDateTime.now().plusMinutes(10));
        assertThat(store.bind("inactive", 56789, 56789)).isEmpty();
    }
}
