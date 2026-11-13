package com.padle.core.padelcoreservice.preview;

import com.padle.core.padelcoreservice.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@Profile("preview")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreviewFixtureService {
    private final JdbcClient jdbc;
    private final PreviewProperties properties;
    private final PasswordEncoder passwords;
    private final ObjectMapper json;
    private final TournamentService tournaments;

    public record Fixtures(long ownerId, long clubId, List<Long> playerIds, List<Long> tournamentIds) { }

    public Fixtures snapshot() {
        String state = jdbc.sql("SELECT state::text FROM ui_preview.control WHERE instance_id=:id AND state IS NOT NULL")
                .param("id", properties.instanceId()).query(String.class).optional()
                .orElseThrow(() -> new IllegalStateException("Preview ещё не подготовлен"));
        return json.readValue(state, Fixtures.class);
    }

    @Transactional
    public Fixtures seed() {
        String state = lock();
        if (!state.isEmpty()) {
            return json.readValue(state, Fixtures.class);
        }
        String hash = passwords.encode(properties.fixturePassword());
        long owner = jdbc.sql("""
                INSERT INTO owners_db(email,password,first_name,last_name,role,is_active)
                VALUES ('preview-admin@example.test',:password,'Preview','Admin','SUPER_ADMIN',true) RETURNING id
                """).param("password", hash).query(Long.class).single();
        long club = jdbc.sql("""
                INSERT INTO club_db(nombre,direccion,created_by,is_active)
                VALUES ('UI v2 Preview','Synthetic',:owner,true) RETURNING id
                """).param("owner", owner).query(Long.class).single();
        var players = IntStream.range(0, 8).mapToObj(index -> player(index, hash)).toList();
        return save(new Fixtures(owner, club, players, events(club, owner, players)));
    }

    @Transactional
    public Fixtures reset() {
        String state = lock();
        if (state.isEmpty()) {
            return seed();
        }
        var fixtures = json.readValue(state, Fixtures.class);
        fixtures.tournamentIds().forEach(this::removeEvent);
        return save(new Fixtures(fixtures.ownerId(), fixtures.clubId(), fixtures.playerIds(),
                events(fixtures.clubId(), fixtures.ownerId(), fixtures.playerIds())));
    }

    private String lock() {
        if (!"padel_ui_v2_preview".equals(jdbc.sql("SELECT current_database()").query(String.class).single())) {
            throw new IllegalStateException("Неверная база preview");
        }
        return jdbc.sql("SELECT COALESCE(state::text,'') FROM ui_preview.control WHERE instance_id=:id FOR UPDATE")
                .param("id", properties.instanceId()).query(String.class).optional()
                .orElseThrow(() -> new IllegalStateException("Маркер preview отсутствует или не совпадает"));
    }

    private long player(int index, String hash) {
        String email = index == 7 ? "preview-guest@1padel.guest" : "preview-player-" + index + "@example.test";
        return jdbc.sql("""
                INSERT INTO player_padel_db(nombre,apellido,email,password_hash,email_confirmado,activo,
                    nivel_jugador,preferred_locale,oauth2_user,telefono)
                VALUES ('Preview',:name,:email,:password,true,true,'C9','es',false,:phone) RETURNING id
                """).param("name", "Player " + index).param("email", email).param("password", hash)
                .param("phone", "+549110000120" + index).query(Long.class).single();
    }

    private List<Long> events(long club, long owner, List<Long> players) {
        var events = IntStream.range(0, 6).mapToObj(index -> event(index, club, owner)).toList();
        registration(events.get(0), players.get(0), "CONFIRMED", false);
        registration(events.get(1), players.get(0), "CONFIRMED", false);
        pair(events.get(2), players.get(0), players.get(1), "CONFIRMED");
        pair(events.get(2), players.get(2), players.get(3), "WAITLIST");
        registration(events.get(3), players.get(0), "SOLO_SEARCH", true);
        pair(events.get(4), players.get(0), players.get(7), "CONFIRMED");
        registration(events.get(5), players.get(0), "CONFIRMED", false);
        tournaments.cancelRegistration(events.get(5), players.get(0), "Preview");
        return events;
    }

    private long event(int index, long club, long owner) {
        String type = switch (index) {
            case 0 -> "KING_OF_COURT";
            case 2, 3, 4 -> "AMERICANO_TEAMS";
            default -> "AMERICANO";
        };
        return jdbc.sql("""
                INSERT INTO tournaments_db(club_id,nombre,fecha_inicio,hora_inicio,genero_formato,categoria_nivel,
                    tipo,modalidad,cupo_max,precio,estado,contacto_organizador,is_active,created_by)
                VALUES (:club,:name,current_date+7,'12:00','MIXTO','C9',:type,:mode,1,0,
                    'REGISTRO_ABIERTO','preview@example.test',true,:owner) RETURNING id
                """).param("club", club).param("owner", owner).param("name", "Preview " + index)
                .param("type", type).param("mode", type.equals("AMERICANO_TEAMS") ? "DOBLES" : "INDIVIDUAL")
                .query(Long.class).single();
    }

    private long registration(long event, long player, String status, boolean paired) {
        return jdbc.sql("""
                INSERT INTO tournament_registrations_db(tournament_id,player_id,main_player_id,status,
                    is_active,is_double_registration,position,waitlist_position,registration_date)
                VALUES (:event,:player,:player,:status,true,:paired,:position,:waiting,localtimestamp) RETURNING id
                """).param("event", event).param("player", player).param("status", status).param("paired", paired)
                .param("position", status.equals("WAITLIST") ? null : 1)
                .param("waiting", status.equals("WAITLIST") ? 1 : null).query(Long.class).single();
    }

    private void pair(long event, long first, long second, String status) {
        long one = registration(event, first, status, true);
        long two = registration(event, second, status, true);
        var booking = UUID.randomUUID();
        jdbc.sql("""
                UPDATE tournament_registrations_db SET main_player_id=:first, pair_booking_id=:booking,
                    partner_id=CASE WHEN player_id=:first THEN :second ELSE :first END WHERE id IN (:one,:two)
                """).param("first", first).param("second", second).param("booking", booking)
                .param("one", one).param("two", two).update();
    }

    @Transactional
    void removeEvent(long id) {
        jdbc.sql("DELETE FROM marketing_events_db WHERE tournament_id=:id").param("id", id).update();
        jdbc.sql("""
                DELETE FROM marketing_payment_progress_db WHERE payment_id IN (SELECT p.id FROM payments_db p
                    JOIN tournament_registrations_db r ON r.id=p.registration_id WHERE r.tournament_id=:id)
                """).param("id", id).update();
        List.of("marketing_events_db", "marketing_paid_slots_db", "marketing_registrations_db", "payments_db")
                .forEach(table -> jdbc.sql("DELETE FROM " + table + " WHERE registration_id IN "
                        + "(SELECT id FROM tournament_registrations_db WHERE tournament_id=:id)").param("id", id).update());
        tournaments.deactivateTournament(id);
        tournaments.deleteTournament(id);
    }

    private Fixtures save(Fixtures fixtures) {
        jdbc.sql("UPDATE ui_preview.control SET state=COALESCE(state,'{}'::jsonb) || CAST(:state AS jsonb) WHERE instance_id=:id")
                .param("state", json.writeValueAsString(fixtures)).param("id", properties.instanceId()).update();
        return fixtures;
    }
}
