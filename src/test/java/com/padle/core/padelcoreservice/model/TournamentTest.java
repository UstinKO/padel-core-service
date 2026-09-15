package com.padle.core.padelcoreservice.model;

import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-374: el nombre del torneo ya no se ingresa manualmente — se genera a partir
 * de generoFormato + categoriaNivel (ej. "Masculino · C6").
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
}
