package com.padle.core.padelcoreservice.mapper;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.Tournament;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * cancellationDeadlineFormatted отражает эффективный дедлайн (индивидуальный, если задан,
 * иначе "старт - 24ч") — тот же, что проверяет canCancelRegistration().
 */
class TournamentMapperTest {

    private final TournamentMapper mapper = new TournamentMapperImpl();

    @Test
    void toDto_conDeadlineIndividual_formateaEseValor() {
        Tournament tournament = Tournament.builder()
                .fechaInicio(LocalDate.of(2026, 10, 25))
                .horaInicio(LocalTime.of(15, 0))
                .deadlineCancelacion(LocalDateTime.of(2026, 10, 23, 15, 0))
                .build();

        TournamentDto dto = mapper.toDto(tournament);

        assertThat(dto.getCancellationDeadlineFormatted()).isEqualTo("23/10/2026 15:00");
    }

    @Test
    void toDto_sinDeadlineIndividual_formateaInicioMenos24h() {
        Tournament tournament = Tournament.builder()
                .fechaInicio(LocalDate.of(2026, 10, 25))
                .horaInicio(LocalTime.of(15, 0))
                .deadlineCancelacion(null)
                .build();

        TournamentDto dto = mapper.toDto(tournament);

        assertThat(dto.getCancellationDeadlineFormatted()).isEqualTo("24/10/2026 15:00");
    }
}
