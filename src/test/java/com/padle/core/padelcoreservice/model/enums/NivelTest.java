package com.padle.core.padelcoreservice.model.enums;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-373: escalas masculina/femenina/mixto completas de {@link Nivel} y filtrado por
 * {@link GenderFormat} en el formulario de torneo.
 */
class NivelTest {

    @Test
    void mascalino_contieneEscalaIndividualCompleta() {
        assertThat(Nivel.C9.getGeneroAplicable()).isEqualTo(GenderFormat.MASCULINO);
        assertThat(List.of(Nivel.C9, Nivel.C8, Nivel.C7, Nivel.C6, Nivel.C5, Nivel.C4))
                .allMatch(nivel -> nivel.getGeneroAplicable() == GenderFormat.MASCULINO);
    }

    @Test
    void masculino_contieneTodasLasParejasAdyacentes() {
        assertThat(List.of(Nivel.C9_C8, Nivel.C8_C7, Nivel.C7_C6, Nivel.C6_C5, Nivel.C5_C4))
                .allMatch(nivel -> nivel.getGeneroAplicable() == GenderFormat.MASCULINO);
    }

    @Test
    void femenino_contieneEscalaIndividualCompleta() {
        assertThat(List.of(Nivel.D9, Nivel.D8, Nivel.D7, Nivel.D6, Nivel.D5, Nivel.D4))
                .allMatch(nivel -> nivel.getGeneroAplicable() == GenderFormat.FEMENINO);
    }

    @Test
    void femenino_contieneTodasLasParejasAdyacentes_reutilizandoD7D8Legacy() {
        // D7_D8 ya existía antes de LFPT-373 (valor legacy usado por torneos existentes) —
        // se reutiliza como la pareja D8/D7 en vez de crear un D8_C7 duplicado.
        assertThat(List.of(Nivel.D9_D8, Nivel.D7_D8, Nivel.D7_D6, Nivel.D6_D5, Nivel.D5_D4))
                .allMatch(nivel -> nivel.getGeneroAplicable() == GenderFormat.FEMENINO);
    }

    @Test
    void mixto_contieneRangoSuma10a16Completo() {
        assertThat(List.of(Nivel.SUMA_10, Nivel.SUMA_11, Nivel.SUMA_12, Nivel.SUMA_13,
                Nivel.SUMA_14, Nivel.SUMA_15, Nivel.SUMA_16))
                .allMatch(nivel -> nivel.getGeneroAplicable() == GenderFormat.MIXTO);
    }

    @Test
    void legacyValues_noTienenGeneroAplicable() {
        assertThat(Nivel.PRINCIPIANTES.getGeneroAplicable()).isNull();
        assertThat(Nivel.TODOS.getGeneroAplicable()).isNull();
        assertThat(Nivel.SIN_ESPECIFICAR.getGeneroAplicable()).isNull();

        assertThat(Nivel.PRINCIPIANTES.isLegacyTournamentLevel()).isTrue();
        assertThat(Nivel.TODOS.isLegacyTournamentLevel()).isTrue();
        assertThat(Nivel.SIN_ESPECIFICAR.isLegacyTournamentLevel()).isTrue();
    }

    @Test
    void forTournamentForm_sinValorActual_excluyeValoresLegacy() {
        List<Nivel> niveles = Nivel.forTournamentForm(null);

        assertThat(niveles).doesNotContain(Nivel.PRINCIPIANTES, Nivel.TODOS, Nivel.SIN_ESPECIFICAR);
        assertThat(niveles).contains(Nivel.C9, Nivel.D4, Nivel.SUMA_10);
    }

    @Test
    void forTournamentForm_conValorActualLegacy_loIncluyeSoloAEse() {
        List<Nivel> niveles = Nivel.forTournamentForm(Nivel.PRINCIPIANTES);

        assertThat(niveles).contains(Nivel.PRINCIPIANTES);
        assertThat(niveles).doesNotContain(Nivel.TODOS, Nivel.SIN_ESPECIFICAR);
    }

    @Test
    void forTournamentForm_conValorActualNoLegacy_comportamientoIgualQueSinValor() {
        List<Nivel> conValor = Nivel.forTournamentForm(Nivel.C7);
        List<Nivel> sinValor = Nivel.forTournamentForm(null);

        assertThat(conValor).isEqualTo(sinValor);
    }

    @Test
    void parseOrNull_valorValido_devuelveElNivel() {
        assertThat(Nivel.parseOrNull("C7")).isEqualTo(Nivel.C7);
    }

    @Test
    void parseOrNull_valorNuloVacioOInvalido_devuelveNull() {
        assertThat(Nivel.parseOrNull(null)).isNull();
        assertThat(Nivel.parseOrNull("")).isNull();
        assertThat(Nivel.parseOrNull("   ")).isNull();
        assertThat(Nivel.parseOrNull("NO_EXISTE")).isNull();
    }
}
