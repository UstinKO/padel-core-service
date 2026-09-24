package com.padle.core.padelcoreservice.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * LFPT-398: calcularMejorPosicion() debe ser null-safe cuando posicionActual
 * todavía no fue calculado (se asigna recién en RankingService.actualizarPosiciones,
 * llamado solo desde getRankingCompleto()) pero mejorPosicion ya tiene un valor.
 */
class RankingTest {

    @Test
    void calcularMejorPosicion_posicionActualNull_noLanzaExcepcionYNoModificaMejorPosicion() {
        Ranking ranking = Ranking.builder().mejorPosicion(3).posicionActual(null).build();

        assertThatCode(ranking::calcularMejorPosicion).doesNotThrowAnyException();
        assertThat(ranking.getMejorPosicion()).isEqualTo(3);
    }

    @Test
    void calcularMejorPosicion_posicionActualYMejorPosicionNull_noLanzaExcepcion() {
        Ranking ranking = Ranking.builder().mejorPosicion(null).posicionActual(null).build();

        assertThatCode(ranking::calcularMejorPosicion).doesNotThrowAnyException();
        assertThat(ranking.getMejorPosicion()).isNull();
    }

    @Test
    void calcularMejorPosicion_posicionActualMejorQueMejorPosicion_actualiza() {
        Ranking ranking = Ranking.builder().mejorPosicion(5).posicionActual(2).build();

        ranking.calcularMejorPosicion();

        assertThat(ranking.getMejorPosicion()).isEqualTo(2);
    }

    @Test
    void calcularMejorPosicion_posicionActualPeorQueMejorPosicion_noCambia() {
        Ranking ranking = Ranking.builder().mejorPosicion(1).posicionActual(4).build();

        ranking.calcularMejorPosicion();

        assertThat(ranking.getMejorPosicion()).isEqualTo(1);
    }
}
