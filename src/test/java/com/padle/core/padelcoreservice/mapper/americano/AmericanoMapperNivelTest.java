package com.padle.core.padelcoreservice.mapper.americano;

import com.padle.core.padelcoreservice.dto.americano.AmericanoPlayerRankingDto;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.americano.AmericanoPlayer;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-483: уровень игрока должен попадать в AmericanoPlayerRankingDto — модель,
 * которую AmericanoViewController (getRankingWithDetails) передаёт в публичный
 * шаблон списка участников Americano.
 */
class AmericanoMapperNivelTest {

    private final AmericanoMapper mapper = Mappers.getMapper(AmericanoMapper.class);

    @Test
    void toRankingDto_jugadorConNivel_mapeaPlayerNivel() {
        PlayerPadel player = PlayerPadel.builder().id(1L).nombre("Juan").apellido("Perez")
                .nivelJugador(Nivel.C7).build();
        AmericanoPlayer americanoPlayer = AmericanoPlayer.builder()
                .id(5L)
                .tournament(Tournament.builder().id(100L).build())
                .player(player)
                .initialPosition(1)
                .build();

        AmericanoPlayerRankingDto dto = mapper.toRankingDto(americanoPlayer);

        assertThat(dto.getPlayerNivel()).isEqualTo(Nivel.C7);
    }

    @Test
    void toRankingDto_jugadorSinNivel_playerNivelEsNull() {
        PlayerPadel player = PlayerPadel.builder().id(1L).nombre("Juan").apellido("Perez")
                .nivelJugador(null).build();
        AmericanoPlayer americanoPlayer = AmericanoPlayer.builder()
                .id(5L)
                .tournament(Tournament.builder().id(100L).build())
                .player(player)
                .initialPosition(1)
                .build();

        AmericanoPlayerRankingDto dto = mapper.toRankingDto(americanoPlayer);

        assertThat(dto.getPlayerNivel()).isNull();
    }
}
