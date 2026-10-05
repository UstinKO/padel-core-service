package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.PlayerStatsDTO;
import com.padle.core.padelcoreservice.model.KingOfCourtPlayerStats;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-483: уровень игрока должен попадать в PlayerStatsDTO — модель, которую
 * King of Court передаёт в публичный шаблон списка участников (через ranking).
 */
class KingOfCourtMapperNivelTest {

    private final KingOfCourtMapper mapper = Mappers.getMapper(KingOfCourtMapper.class);

    @Test
    void toPlayerStatsDTO_jugadorConNivel_mapeaPlayerNivel() {
        PlayerPadel player = PlayerPadel.builder().id(1L).nombre("Juan").apellido("Perez")
                .nivelJugador(Nivel.D6).build();
        KingOfCourtPlayerStats stats = new KingOfCourtPlayerStats();
        stats.setPlayer(player);
        stats.setTotalPoints(10);

        PlayerStatsDTO dto = mapper.toPlayerStatsDTO(stats);

        assertThat(dto.getPlayerNivel()).isEqualTo(Nivel.D6);
    }

    @Test
    void toPlayerStatsDTO_jugadorSinNivel_playerNivelEsNull() {
        // PlayerPadel.nivelJugador es NOT NULL en BD — "sin especificar" es SIN_ESPECIFICAR.
        PlayerPadel player = PlayerPadel.builder().id(1L).nombre("Juan").apellido("Perez")
                .nivelJugador(Nivel.SIN_ESPECIFICAR).build();
        KingOfCourtPlayerStats stats = new KingOfCourtPlayerStats();
        stats.setPlayer(player);
        stats.setTotalPoints(10);

        PlayerStatsDTO dto = mapper.toPlayerStatsDTO(stats);

        assertThat(dto.getPlayerNivel()).isNull();
    }
}
