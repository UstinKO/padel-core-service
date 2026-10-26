package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Правило "±1 ступень" для самостоятельной регистрации на категорийный турнир.
 * Чистая логика {@link TournamentLevelPolicy#evaluate}, без Spring-контекста.
 */
class TournamentLevelPolicyTest {

    @ParameterizedTest
    @CsvSource({
            "C9, C9", "C9, C8",
            "C8, C9", "C8, C8", "C8, C7",
            "C7, C8", "C7, C7", "C7, C6",
            "C6, C7", "C6, C6", "C6, C5",
            "C4, C5", "C4, C4",
            "D9, D9", "D9, D8",
            "D6, D7", "D6, D6",
    })
    void dentroDeUnaCategoriaDeDiferencia_permiteRegistro(Nivel jugador, Nivel categoria) {
        TournamentLevelPolicy.Result result =
                TournamentLevelPolicy.evaluate(TournamentType.KING_OF_COURT, categoria, jugador);
        assertThat(result.verdict()).isEqualTo(TournamentLevelPolicy.Verdict.ALLOWED);
    }

    @ParameterizedTest
    @CsvSource({
            "C9, C7", "C9, C6",
            "C8, C6", "C8, C5",
            "C7, C9", "C7, C5",
            "C6, C8", "C6, C4",
    })
    void masDeUnaCategoriaDeDiferencia_bloqueaConMensajeDeNivelLejano(Nivel jugador, Nivel categoria) {
        TournamentLevelPolicy.Result result =
                TournamentLevelPolicy.evaluate(TournamentType.AMERICANO, categoria, jugador);
        assertThat(result.verdict()).isEqualTo(TournamentLevelPolicy.Verdict.LEVEL_MISMATCH);
        assertThat(result.allowedLevels()).isNotEmpty().allMatch(Nivel::isPlayerLevel);
    }

    @Test
    void ejemploDelCliente_c8NoPuedeRegistrarseEnC6_yListaIncluyeEquivalentesFemeninos() {
        TournamentLevelPolicy.Result result =
                TournamentLevelPolicy.evaluate(TournamentType.AMERICANO, Nivel.C6, Nivel.C8);

        assertThat(result.verdict()).isEqualTo(TournamentLevelPolicy.Verdict.LEVEL_MISMATCH);
        // Расчётный уровень C8 — 8: D7 (расчёт 8) и D8 (расчёт 9) в пределах одной ступени,
        // как и C9/C7; D6 (расчёт 7) тоже входит как нижняя граница.
        assertThat(result.allowedLevels())
                .containsExactly(Nivel.C9, Nivel.C8, Nivel.C7, Nivel.D8, Nivel.D7, Nivel.D6);
    }

    @Test
    void jugadorExtremoC4_soloTieneDosCategoriasPermitidas() {
        TournamentLevelPolicy.Result result =
                TournamentLevelPolicy.evaluate(TournamentType.AMERICANO, Nivel.C6, Nivel.C4);

        // Расчётный уровень C4 (4) слишком далёк от любой женской категории (D6 — самая
        // слабая, допустимая для турниров, расчёт 7) — список остаётся только мужским.
        assertThat(result.allowedLevels()).containsExactly(Nivel.C5, Nivel.C4);
    }

    @Test
    void jugadorExtremoD6_noIncluyeD5EnPermitidos_porqueD5NoEsNivelDeJugador() {
        // D5/D4 не назначаются игроку — хотя D6 в одной ступени от D5, D5 никогда не
        // появится в списке "допустимых категорий". Категория турнира D9 (далёкая от D6)
        // используется только чтобы форсировать вычисление списка.
        TournamentLevelPolicy.Result result =
                TournamentLevelPolicy.evaluate(TournamentType.AMERICANO, Nivel.D9, Nivel.D6);

        // Расчётный уровень D6 — 7: совпадает с C7, в пределах одной ступени от C8/C6/D7.
        assertThat(result.allowedLevels())
                .containsExactly(Nivel.C8, Nivel.C7, Nivel.C6, Nivel.D7, Nivel.D6)
                .doesNotContain(Nivel.D5);
    }

    @ParameterizedTest
    @CsvSource({
            "D6, C7, ALLOWED",
            "D7, C7, ALLOWED",
            "D8, C7, LEVEL_MISMATCH",
            "D9, C7, LEVEL_MISMATCH",
            "D8, C9, ALLOWED",
            "D9, C9, ALLOWED",
            "D9, C8, LEVEL_MISMATCH",
            "C7, D6, ALLOWED",
            "C9, D6, LEVEL_MISMATCH",
            "C7, C9, LEVEL_MISMATCH",
            "C9, C7, LEVEL_MISMATCH",
    })
    void escalaDeEquivalenciaCD_parejasDeReferenciaDelCliente(
            Nivel jugador, Nivel categoria, TournamentLevelPolicy.Verdict esperado) {
        TournamentLevelPolicy.Result result =
                TournamentLevelPolicy.evaluate(TournamentType.AMERICANO, categoria, jugador);
        assertThat(result.verdict()).isEqualTo(esperado);
    }

    @Test
    void generoDistintoConNivelCalculadoCercano_permiteRegistro() {
        // D6 (расчётный уровень 7) и C7 (7) эквивалентны по шкале 1-Padel — пол сам по себе
        // больше не блокирует, только расстояние по расчётной шкале.
        TournamentLevelPolicy.Result result =
                TournamentLevelPolicy.evaluate(TournamentType.AMERICANO, Nivel.C7, Nivel.D6);

        assertThat(result.verdict()).isEqualTo(TournamentLevelPolicy.Verdict.ALLOWED);
    }

    @Test
    void generoDistintoConNivelCalculadoLejano_bloqueaPorNivelNoPorGenero() {
        // C4 (4) и D9 (10) далеко друг от друга по расчётной шкале — блокировка из-за
        // расстояния, а не из-за явной проверки пола (её больше нет).
        TournamentLevelPolicy.Result result =
                TournamentLevelPolicy.evaluate(TournamentType.AMERICANO, Nivel.D9, Nivel.C4);

        assertThat(result.verdict()).isEqualTo(TournamentLevelPolicy.Verdict.LEVEL_MISMATCH);
    }

    @ParameterizedTest
    @EnumSource(value = Nivel.class, names = {"SIN_ESPECIFICAR"})
    void nivelNoEspecificado_bloqueaConVerdictLevelRequired(Nivel sinEspecificar) {
        TournamentLevelPolicy.Result result =
                TournamentLevelPolicy.evaluate(TournamentType.AMERICANO, Nivel.C7, sinEspecificar);

        assertThat(result.verdict()).isEqualTo(TournamentLevelPolicy.Verdict.LEVEL_REQUIRED);
    }

    @Test
    void nivelJugadorNull_bloqueaConVerdictLevelRequired() {
        TournamentLevelPolicy.Result result =
                TournamentLevelPolicy.evaluate(TournamentType.AMERICANO, Nivel.C7, null);

        assertThat(result.verdict()).isEqualTo(TournamentLevelPolicy.Verdict.LEVEL_REQUIRED);
    }

    @ParameterizedTest
    @EnumSource(value = Nivel.class, names = {"TODOS", "PRINCIPIANTES", "SIN_ESPECIFICAR",
            "C9_C8", "C7_C6", "D7_D8", "D5_D4", "SUMA_13", "SUMA_16", "D5", "D4"})
    void categoriasNoSimples_noAplicanElChequeoIndependientementeDelNivelDelJugador(Nivel categoria) {
        // Ninguna de estas categorías es Nivel.isPlayerLevel — el chequeo no se aplica,
        // incluso para un jugador sin nivel especificado (#303: SUMA_*, rangos, TODOS,
        // PRINCIPIANTES y D5/D4 quedan fuera de esta regla, no solo "lejos" del jugador).
        TournamentLevelPolicy.Result result =
                TournamentLevelPolicy.evaluate(TournamentType.AMERICANO, categoria, Nivel.SIN_ESPECIFICAR);

        assertThat(result.verdict()).isEqualTo(TournamentLevelPolicy.Verdict.ALLOWED);
    }

    @Test
    void categoriaNull_noAplicaElChequeo() {
        TournamentLevelPolicy.Result result =
                TournamentLevelPolicy.evaluate(TournamentType.AMERICANO, null, Nivel.SIN_ESPECIFICAR);

        assertThat(result.verdict()).isEqualTo(TournamentLevelPolicy.Verdict.ALLOWED);
    }

    @ParameterizedTest
    @EnumSource(value = TournamentType.class, names = {"CANCHA_ABIERTA", "PADEL_CLINIC"})
    void formatosAbiertos_noAplicanElChequeoIndependientementeDeLaCategoria(TournamentType tipo) {
        TournamentLevelPolicy.Result result =
                TournamentLevelPolicy.evaluate(tipo, Nivel.C6, Nivel.SIN_ESPECIFICAR);

        assertThat(result.verdict()).isEqualTo(TournamentLevelPolicy.Verdict.ALLOWED);
    }

    @ParameterizedTest
    @EnumSource(value = TournamentType.class, names = {"KING_OF_COURT", "AMERICANO", "AMERICANO_TEAMS"})
    void formatosDeCategoria_siAplicanElChequeo(TournamentType tipo) {
        TournamentLevelPolicy.Result result =
                TournamentLevelPolicy.evaluate(tipo, Nivel.C6, Nivel.C9);

        assertThat(result.verdict()).isEqualTo(TournamentLevelPolicy.Verdict.LEVEL_MISMATCH);
    }
}
