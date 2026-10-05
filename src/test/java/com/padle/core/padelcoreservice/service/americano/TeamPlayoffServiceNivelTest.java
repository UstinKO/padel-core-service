package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.dto.americano.AmericanoTeamDto;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoTeamRepository;
import com.padle.core.padelcoreservice.service.WebSocketService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * LFPT-483: уровень игроков команды (player1Nivel/player2Nivel) должен попадать
 * в AmericanoTeamDto — модель, которую Team Americano / Team Playoff передают
 * в публичный шаблон списка участников (TeamPlayoffService.toDto).
 */
class TeamPlayoffServiceNivelTest {

    private final TeamPlayoffService service = new TeamPlayoffService(
            mock(TournamentRepository.class),
            mock(AmericanoTeamRepository.class),
            mock(AmericanoRoundRepository.class),
            mock(AmericanoMatchRepository.class),
            mock(PlayerRepository.class),
            mock(TournamentRegistrationRepository.class),
            mock(WebSocketService.class),
            mock(PlayoffMatchingEngine.class)
    );

    @Test
    void toDto_ambosJugadoresConNivel_mapeaPlayer1YPlayer2Nivel() {
        PlayerPadel player1 = PlayerPadel.builder().id(1L).nombre("Juan").apellido("Perez")
                .nivelJugador(Nivel.C6).build();
        PlayerPadel player2 = PlayerPadel.builder().id(2L).nombre("Ana").apellido("Gomez")
                .nivelJugador(Nivel.D7).build();
        AmericanoTeam team = AmericanoTeam.builder()
                .id(1L)
                .tournament(Tournament.builder().id(100L).build())
                .teamNumber(1)
                .player1(player1)
                .player2(player2)
                .build();

        AmericanoTeamDto dto = service.toDto(team);

        assertThat(dto.getPlayer1Nivel()).isEqualTo(Nivel.C6);
        assertThat(dto.getPlayer2Nivel()).isEqualTo(Nivel.D7);
    }

    @Test
    void toDto_jugadorGuestSinPerfil_player2NivelEsNull() {
        PlayerPadel player1 = PlayerPadel.builder().id(1L).nombre("Juan").apellido("Perez")
                .nivelJugador(Nivel.C6).build();
        AmericanoTeam team = AmericanoTeam.builder()
                .id(1L)
                .tournament(Tournament.builder().id(100L).build())
                .teamNumber(1)
                .player1(player1)
                .player2(null)
                .player2Name("Invitado")
                .build();

        AmericanoTeamDto dto = service.toDto(team);

        assertThat(dto.getPlayer1Nivel()).isEqualTo(Nivel.C6);
        assertThat(dto.getPlayer2Nivel()).isNull();
    }
}
