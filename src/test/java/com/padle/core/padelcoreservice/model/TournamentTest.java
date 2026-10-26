package com.padle.core.padelcoreservice.model;

import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Название турнира не вводится вручную — собирается из generoFormato + categoriaNivel
 * (например, "Masculino · C6").
 */
class TournamentTest {

    @Test
    void generateNombre_generoYNivelPresentes_combinaAmbos() {
        assertThat(Tournament.generateNombre(GenderFormat.MASCULINO, Nivel.C6))
                .isEqualTo("Masculino · C6");
    }

    @Test
    void generateNombre_conNivelLegacy_usaElNombreDelEnum() {
        assertThat(Tournament.generateNombre(GenderFormat.MASCULINO, Nivel.TODOS))
                .isEqualTo("Masculino · TODOS");
    }

    @Test
    void generateNombre_sinCategoriaNivel_devuelveSoloElGenero() {
        assertThat(Tournament.generateNombre(GenderFormat.MIXTO, null)).isEqualTo("Mixto");
    }

    @Test
    void generateNombre_sinGeneroFormato_devuelveSoloElNivel() {
        assertThat(Tournament.generateNombre(null, Nivel.D7)).isEqualTo("D7");
    }

    @Test
    void generateNombre_sinNingunDato_devuelveValorPorDefecto() {
        assertThat(Tournament.generateNombre(null, null)).isEqualTo("Torneo");
    }

    // Единый источник дедлайна отмены — то, что реально проверяет
    // canCancelRegistration(), должно совпадать с тем, что показывается игроку.

    @Test
    void getEffectiveDeadlineCancelacion_conDeadlineIndividual_devuelveElIndividual() {
        Tournament tournament = Tournament.builder()
                .fechaInicio(LocalDate.of(2026, 10, 25))
                .horaInicio(LocalTime.of(15, 0))
                .deadlineCancelacion(LocalDateTime.of(2026, 10, 23, 15, 0))
                .build();

        assertThat(tournament.getEffectiveDeadlineCancelacion())
                .isEqualTo(LocalDateTime.of(2026, 10, 23, 15, 0));
    }

    @Test
    void getEffectiveDeadlineCancelacion_sinDeadlineIndividual_devuelveInicioMenos24h() {
        Tournament tournament = Tournament.builder()
                .fechaInicio(LocalDate.of(2026, 10, 25))
                .horaInicio(LocalTime.of(15, 0))
                .deadlineCancelacion(null)
                .build();

        assertThat(tournament.getEffectiveDeadlineCancelacion())
                .isEqualTo(LocalDateTime.of(2026, 10, 24, 15, 0));
    }

    @Test
    void getFormattedCancellationDeadline_formateaFechaYHora() {
        Tournament tournament = Tournament.builder()
                .fechaInicio(LocalDate.of(2026, 10, 25))
                .horaInicio(LocalTime.of(15, 0))
                .deadlineCancelacion(LocalDateTime.of(2026, 10, 23, 15, 0))
                .build();

        assertThat(tournament.getFormattedCancellationDeadline()).isEqualTo("23/10/2026 15:00");
    }

    @Test
    void canCancelRegistration_antesDelDeadlineIndividual_true() {
        Tournament tournament = Tournament.builder()
                .fechaInicio(LocalDate.now().plusDays(3))
                .horaInicio(LocalTime.of(15, 0))
                .deadlineCancelacion(LocalDateTime.now().plusDays(1))
                .build();

        assertThat(tournament.canCancelRegistration()).isTrue();
    }

    @Test
    void canCancelRegistration_despuesDelDeadlineIndividual_false() {
        Tournament tournament = Tournament.builder()
                .fechaInicio(LocalDate.now().plusDays(3))
                .horaInicio(LocalTime.of(15, 0))
                .deadlineCancelacion(LocalDateTime.now().minusHours(1))
                .build();

        assertThat(tournament.canCancelRegistration()).isFalse();
    }
}
