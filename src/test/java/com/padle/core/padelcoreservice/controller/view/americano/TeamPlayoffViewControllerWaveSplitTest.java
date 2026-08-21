package com.padle.core.padelcoreservice.controller.view.americano;

import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoRoundDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-348: квалификация Team Playoff технически хранится как единый round-контейнер
 * (см. {@code TeamPlayoffService.getOrCreateQualificationRound}) — {@link TeamPlayoffViewController#splitQualificationWaves}
 * разбивает его на до 2 визуальных волн ("Раунд 1"/"Раунд 2") и сортирует матчи внутри волны по корту.
 * Не требует Spring-контекста — метод не обращается к внедрённым зависимостям контроллера.
 */
class TeamPlayoffViewControllerWaveSplitTest {

    private final TeamPlayoffViewController controller = new TeamPlayoffViewController(null, null, null);

    @Test
    void splitsMatchesIntoTwoWaves_sortedByCourtWithinEachWave() {
        AmericanoRoundDto container = AmericanoRoundDto.builder()
                .id(1L)
                .tournamentId(100L)
                .roundNumber(1)
                .matches(List.of(
                        match(1L, 2, 1, 1),  // волна 1, корт 2
                        match(2L, 1, 1, 1),  // волна 1, корт 1
                        match(3L, 4, 2, 2),  // волна 2, корт 4
                        match(4L, 3, 2, 2)   // волна 2, корт 3
                ))
                .build();

        List<AmericanoRoundDto> waves = controller.splitQualificationWaves(List.of(container));

        assertThat(waves).hasSize(2);
        assertThat(waves.get(0).getRoundNumber()).isEqualTo(1);
        assertThat(waves.get(0).getMatches()).extracting(AmericanoMatchDto::getId).containsExactly(2L, 1L);
        assertThat(waves.get(0).getTotalMatches()).isEqualTo(2);

        assertThat(waves.get(1).getRoundNumber()).isEqualTo(2);
        assertThat(waves.get(1).getMatches()).extracting(AmericanoMatchDto::getId).containsExactly(4L, 3L);
    }

    @Test
    void onlyFirstWaveMatchesExist_returnsSingleWave() {
        AmericanoRoundDto container = AmericanoRoundDto.builder()
                .roundNumber(1)
                .matches(List.of(match(1L, 2, 1, 1), match(2L, 1, 1, 1)))
                .build();

        List<AmericanoRoundDto> waves = controller.splitQualificationWaves(List.of(container));

        assertThat(waves).hasSize(1);
        assertThat(waves.get(0).getRoundNumber()).isEqualTo(1);
    }

    /** T14: "дополнительная" команда играет свой 1-й матч против соперника, для которого это уже 2-й — относится к волне 2. */
    @Test
    void mismatchedOrdinals_assignedToMaxWave() {
        AmericanoRoundDto container = AmericanoRoundDto.builder()
                .roundNumber(1)
                .matches(List.of(match(1L, 1, 1, 2)))
                .build();

        List<AmericanoRoundDto> waves = controller.splitQualificationWaves(List.of(container));

        assertThat(waves).hasSize(1);
        assertThat(waves.get(0).getRoundNumber()).isEqualTo(2);
    }

    @Test
    void emptyInput_returnsEmptyList() {
        assertThat(controller.splitQualificationWaves(List.of())).isEmpty();
    }

    private AmericanoMatchDto match(Long id, int courtNumber, int ordinal1, int ordinal2) {
        return AmericanoMatchDto.builder()
                .id(id)
                .courtNumber(courtNumber)
                .team1MatchOrdinal(ordinal1)
                .team2MatchOrdinal(ordinal2)
                .isCompleted(false)
                .build();
    }
}
