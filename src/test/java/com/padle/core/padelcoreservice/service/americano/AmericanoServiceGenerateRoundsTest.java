package com.padle.core.padelcoreservice.service.americano;

import com.padle.core.padelcoreservice.dto.americano.AmericanoConfigDto;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.americano.AmericanoPlayer;
import com.padle.core.padelcoreservice.model.enums.AmericanoFormatType;
import com.padle.core.padelcoreservice.model.enums.AmericanoPlayerStatus;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoPlayerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Запрос {@code generationMode=FULL} на конфигурации, где полный цикл без bye математически
 * невозможен, должен завершаться понятной ошибкой до генерации — не падать внутри движка
 * и не сохранять некорректное расписание.
 */
@ExtendWith(MockitoExtension.class)
class AmericanoServiceGenerateRoundsTest {

    private static final long TOURNAMENT_ID = 1L;

    @Mock private TournamentRepository tournamentRepository;
    @Mock private AmericanoPlayerRepository americanoPlayerRepository;

    @InjectMocks private AmericanoService americanoService;

    @Test
    void fullModeRequested_onConfigurationWithUnavoidableBye_throwsBeforeGenerating() {
        Tournament tournament = Tournament.builder().id(TOURNAMENT_ID).build();
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament));

        // 7 игроков, 1 корт (playersPerRound=4) — bye неизбежен, Full невозможен.
        List<AmericanoPlayer> players = LongStream.rangeClosed(1, 7)
                .mapToObj(id -> AmericanoPlayer.builder()
                        .player(PlayerPadel.builder().id(id).build())
                        .status(AmericanoPlayerStatus.ACTIVE)
                        .build())
                .toList();
        when(americanoPlayerRepository.findByTournamentIdAndStatus(TOURNAMENT_ID, AmericanoPlayerStatus.ACTIVE))
                .thenReturn(players);

        AmericanoConfigDto config = AmericanoConfigDto.builder()
                .courts(1)
                .totalRounds(6)
                .generationMode(AmericanoFormatType.FULL)
                .build();

        assertThatThrownBy(() -> americanoService.generateRounds(TOURNAMENT_ID, config, AmericanoFormatType.FULL))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("imposible");
    }
}
