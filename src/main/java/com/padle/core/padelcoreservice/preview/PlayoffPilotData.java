package com.padle.core.padelcoreservice.preview;

import com.padle.core.padelcoreservice.dto.americano.TeamPlayoffAddTeamRequest;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.enums.TournamentPhase;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.preview.PlayoffPilotFixtureService.Accounts;
import com.padle.core.padelcoreservice.preview.PlayoffPilotFixtureService.Scenario;
import com.padle.core.padelcoreservice.preview.PlayoffPilotFixtureService.Stage;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.service.americano.TeamPlayoffService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

@Component
@Profile("preview")
@RequiredArgsConstructor
class PlayoffPilotData {
    private final JdbcClient jdbc;
    private final PreviewProperties properties;
    private final PasswordEncoder passwords;
    private final TeamPlayoffService playoff;
    private final AmericanoMatchRepository matches;
    private final TournamentRepository tournaments;

    Accounts accounts() {
        String hash = passwords.encode(properties.fixturePassword());
        long club = club("Playoff pilot");
        long foreignClub = club("Playoff pilot other club");
        long manager = manager("pilot-manager@example.test", club, hash);
        long foreignManager = manager("pilot-foreign@example.test", foreignClub, hash);
        var players = IntStream.range(0, 32).mapToObj(index -> player(index, hash)).toList();
        return new Accounts(manager, foreignManager, club, foreignClub, players);
    }

    List<Scenario> scenarios(Accounts accounts) {
        return List.of(scenario(accounts, 8, Stage.ROSTER), scenario(accounts, 8, Stage.QUALIFIED),
                scenario(accounts, 9, Stage.QUALIFIED), scenario(accounts, 16, Stage.QUALIFIED),
                scenario(accounts, 8, Stage.FINISHED));
    }

    private Scenario scenario(Accounts accounts, int count, Stage stage) {
        String key = stage.name().toLowerCase(Locale.ROOT) + "-" + count;
        long event = event(accounts, key, count);
        var teams = IntStream.range(0, count).mapToObj(index -> team(event, accounts.playerIds(), index)).toList();
        if (stage != Stage.ROSTER) {
            qualify(event, teams);
        }
        if (stage == Stage.FINISHED) {
            finish(event);
        }
        return new Scenario(key, count, stage, event, teams);
    }

    private long club(String name) {
        return jdbc.sql("INSERT INTO club_db(nombre,direccion,is_active) VALUES (:name,'Synthetic',true) RETURNING id")
                .param("name", name).query(Long.class).single();
    }

    private long manager(String email, long club, String hash) {
        return jdbc.sql("""
                INSERT INTO owners_db(email,password,first_name,last_name,role,club_id,is_active)
                VALUES (:email,:password,'Pilot','Manager','CLUB_ADMIN',:club,true) RETURNING id
                """).param("email", email).param("password", hash).param("club", club).query(Long.class).single();
    }

    private long player(int index, String hash) {
        return jdbc.sql("""
                INSERT INTO player_padel_db(nombre,apellido,email,password_hash,email_confirmado,activo,
                    nivel_jugador,preferred_locale,oauth2_user,telefono)
                VALUES ('Pilot',:name,:email,:password,true,true,'C9','es',false,:phone) RETURNING id
                """).param("name", "Player " + index).param("email", "pilot-player-" + index + "@example.test")
                .param("password", hash).param("phone", "+549110009" + "%04d".formatted(index)).query(Long.class).single();
    }

    private long event(Accounts accounts, String key, int count) {
        return jdbc.sql("""
                INSERT INTO tournaments_db(club_id,nombre,fecha_inicio,hora_inicio,genero_formato,categoria_nivel,
                    tipo,modalidad,cupo_max,precio,estado,contacto_organizador,is_active,created_by,owner_id)
                VALUES (:club,:name,current_date+7,'12:00','MIXTO','C9','AMERICANO_TEAMS','DOBLES',:capacity,0,
                    'REGISTRO_ABIERTO','pilot@example.test',true,:owner,:owner) RETURNING id
                """).param("club", accounts.clubId()).param("name", "Playoff pilot " + key)
                .param("capacity", count).param("owner", accounts.managerId()).query(Long.class).single();
    }

    private long team(long event, List<Long> players, int index) {
        var request = new TeamPlayoffAddTeamRequest();
        request.setPlayer1Id(players.get(index * 2));
        request.setPlayer2Id(players.get(index * 2 + 1));
        request.setRegistrationSource("ADMIN_MANUAL");
        request.setHasPaid(true);
        request.setAttended(true);
        return playoff.addTeam(event, request).getId();
    }

    private void qualify(long event, List<Long> teams) {
        playoff.initQualification(event, 8);
        int score = 0;
        for (int index = 0; index + 1 < teams.size(); index += 2) {
            playQualification(event, teams.get(index), teams.get(index + 1), score++);
        }
        for (int pass = 0; pass < teams.size() * 2 && !qualified(event, teams); pass++) {
            var suggestions = playoff.suggestSecondRoundPairings(event);
            if (suggestions.isEmpty()) {
                throw new IllegalStateException("Не удалось подготовить квалификацию пилота");
            }
            for (var suggestion : suggestions) {
                playQualification(event, suggestion.getTeam1().getId(), suggestion.getTeam2().getId(), score++);
            }
        }
        if (!qualified(event, teams)) {
            throw new IllegalStateException("Квалификация пилота не завершена");
        }
    }

    private void playQualification(long event, long first, long second, int index) {
        long match = playoff.createQualificationMatch(event, first, second, 1).getId();
        playoff.submitQualResult(match, 6, index % 6);
    }

    private boolean qualified(long event, List<Long> teams) {
        var completed = matches.findByTournamentIdAndPhase(event, TournamentPhase.QUALIFICATION).stream()
                .filter(AmericanoMatch::isCompleted).toList();
        return teams.stream().allMatch(team -> completed.stream()
                .filter(match -> team.equals(match.getTeam1Id()) || team.equals(match.getTeam2Id())).count() == 2);
    }

    private void finish(long event) {
        playoff.initPlayoff(event);
        for (int stage = 0; stage < 8; stage++) {
            var current = matches.findByTournamentIdOrderByRoundIdAscMatchNumberAsc(event).stream()
                    .filter(AmericanoMatch::isInProgress).filter(match -> match.getPlayoffStage() != null).toList();
            if (current.isEmpty()) {
                break;
            }
            current.forEach(match -> playoff.submitPlayoffResult(match.getId(), 6, 3));
        }
        if (tournaments.findById(event).orElseThrow().getEstado() != TournamentStatus.FINALIZADO) {
            throw new IllegalStateException("Финал пилота не завершён");
        }
    }
}
