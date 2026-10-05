package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-483: уровень игрока/партнёра (PlayerPadel.nivelJugador) должен попадать
 * в TournamentRegistrationDto — модель, которую bracket/Cancha Abierta
 * передают в публичный шаблон списка участников.
 */
class TournamentRegistrationMapperNivelTest {

    private final TournamentRegistrationMapper mapper = new TournamentRegistrationMapperImpl();

    @Test
    void toDto_jugadorConNivel_mapeaPlayerNivel() {
        PlayerPadel player = PlayerPadel.builder().id(1L).nombre("Juan").apellido("Perez")
                .nivelJugador(Nivel.C6).build();
        TournamentRegistration registration = TournamentRegistration.builder()
                .id(10L)
                .tournament(Tournament.builder().id(100L).build())
                .player(player)
                .status(RegistrationStatus.CONFIRMED)
                .build();

        TournamentRegistrationDto dto = mapper.toDto(registration);

        assertThat(dto.getPlayerNivel()).isEqualTo(Nivel.C6);
    }

    @Test
    void toDto_jugadorSinNivel_playerNivelEsNull() {
        PlayerPadel player = PlayerPadel.builder().id(1L).nombre("Juan").apellido("Perez")
                .nivelJugador(null).build();
        TournamentRegistration registration = TournamentRegistration.builder()
                .id(10L)
                .tournament(Tournament.builder().id(100L).build())
                .player(player)
                .status(RegistrationStatus.CONFIRMED)
                .build();

        TournamentRegistrationDto dto = mapper.toDto(registration);

        assertThat(dto.getPlayerNivel()).isNull();
    }

    @Test
    void toDto_conPartnerConfirmado_mapeaPartnerNivel() {
        PlayerPadel player = PlayerPadel.builder().id(1L).nombre("Juan").apellido("Perez")
                .nivelJugador(Nivel.C6).build();
        PlayerPadel partner = PlayerPadel.builder().id(2L).nombre("Ana").apellido("Gomez")
                .nivelJugador(Nivel.D7).build();
        TournamentRegistration registration = TournamentRegistration.builder()
                .id(10L)
                .tournament(Tournament.builder().id(100L).build())
                .player(player)
                .partner(partner)
                .isDoubleRegistration(true)
                .status(RegistrationStatus.CONFIRMED)
                .build();

        TournamentRegistrationDto dto = mapper.toDto(registration);

        assertThat(dto.getPartnerNivel()).isEqualTo(Nivel.D7);
    }

    @Test
    void toDto_sinPartnerConfirmado_partnerNivelEsNull() {
        PlayerPadel player = PlayerPadel.builder().id(1L).nombre("Juan").apellido("Perez")
                .nivelJugador(Nivel.C6).build();
        TournamentRegistration registration = TournamentRegistration.builder()
                .id(10L)
                .tournament(Tournament.builder().id(100L).build())
                .player(player)
                .isDoubleRegistration(true)
                .status(RegistrationStatus.PAIR_REGISTERED)
                .build();

        TournamentRegistrationDto dto = mapper.toDto(registration);

        assertThat(dto.getPartnerNivel()).isNull();
    }
}
