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
    void ejemploDelCliente_c8NoPuedeRegistrarseEnC6_yListaC9C8C7() {
        TournamentLevelPolicy.Result result =
                TournamentLevelPolicy.evaluate(TournamentType.AMERICANO, Nivel.C6, Nivel.C8);

        assertThat(result.verdict()).isEqualTo(TournamentLevelPolicy.Verdict.LEVEL_MISMATCH);
        assertThat(result.allowedLevels()).containsExactly(Nivel.C9, Nivel.C8, Nivel.C7);
    }

    @Test
    void jugadorExtremoC9_soloTieneDosCategoriasPermitidas() {
        TournamentLevelPolicy.Result result =
                TournamentLevelPolicy.evaluate(TournamentType.AMERICANO, Nivel.C7, Nivel.C9);

        assertThat(result.allowedLevels()).containsExactly(Nivel.C9, Nivel.C8);
    }

    @Test
    void jugadorExtremoC4_soloTieneDosCategoriasPermitidas() {
        TournamentLevelPolicy.Result result =
                TournamentLevelPolicy.evaluate(TournamentType.AMERICANO, Nivel.C6, Nivel.C4);

        assertThat(result.allowedLevels()).containsExactly(Nivel.C5, Nivel.C4);
    }

    @Test
    void jugadorExtremoD6_noIncluyeD5EnPermitidos_porqueD5NoEsNivelDeJugador() {
        // D5/D4 нет в Nivel.PLAYER_LEVELS — хотя D6 в одной категории от D5, D5 никогда не может
        // оказаться в "разрешённых категориях". D9 (далеко от D6) берётся категорией турнира
        // только чтобы форсировать расчёт списка.
        TournamentLevelPolicy.Result result =
                TournamentLevelPolicy.evaluate(TournamentType.AMERICANO, Nivel.D9, Nivel.D6);

        assertThat(result.allowedLevels()).containsExactly(Nivel.D7, Nivel.D6);
    }

    @Test
    void generoDistinto_bloqueaAunqueOrdinalEsteCerca() {
        // C4 (masculino, ordinal 5) y D9 (femenino, ordinal 11) nunca deberían mezclarse;
        // este test fija la intención explícita del chequeo de género, no solo el ordinal.
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
        // Ни одна из этих категорий не Nivel.isPlayerLevel — проверка не применяется даже
        // для игрока без указанного уровня (SUMA_*, диапазоны, TODOS, PRINCIPIANTES и D5/D4
        // вне этого правила, а не просто "далеко" от игрока).
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
