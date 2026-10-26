package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.exception.PlayerLevelRequiredException;
import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import org.springframework.context.MessageSource;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Правило "±1 ступень" для самостоятельной регистрации на категорийный турнир.
 * Чистая логика, без зависимостей от Spring — i18n-сообщения собирает вызывающий код
 * (TournamentService / DoubleTournamentRegistrationService) на основе {@link Result}.
 */
public final class TournamentLevelPolicy {

    private static final int MAX_STEP_DIFFERENCE = 1;

    // Открытые форматы, где организатор не ограничивает участников по уровню —
    // правило не применяется независимо от categoriaNivel.
    private static final Set<TournamentType> EXEMPT_TYPES =
            EnumSet.of(TournamentType.CANCHA_ABIERTA, TournamentType.PADEL_CLINIC);

    private TournamentLevelPolicy() {
    }

    public enum Verdict {
        ALLOWED,
        LEVEL_REQUIRED,
        LEVEL_MISMATCH
    }

    public record Result(Verdict verdict, List<Nivel> allowedLevels) {
        public static Result allowed() {
            return new Result(Verdict.ALLOWED, List.of());
        }
    }

    // Правило применяется только к "простым" категориям, напрямую сопоставимым с уровнем
    // игрока (Nivel.isPlayerLevel — те же 10 значений, что и PlayerPadel.nivelJugador).
    // Диапазоны (C9_C8, D7_D8...), TODOS, PRINCIPIANTES, SUMA_* и D5/D4 (ещё не назначаемые
    // игроку) — структурно "непростые" категории и остаются без ограничения.
    public static boolean requiresLevelCheck(TournamentType tipo, Nivel categoriaNivel) {
        return Nivel.isPlayerLevel(categoriaNivel) && !EXEMPT_TYPES.contains(tipo);
    }

    public static Result evaluate(TournamentType tipo, Nivel categoriaNivel, Nivel playerLevel) {
        if (!requiresLevelCheck(tipo, categoriaNivel)) {
            return Result.allowed();
        }
        if (playerLevel == null || playerLevel == Nivel.SIN_ESPECIFICAR) {
            return new Result(Verdict.LEVEL_REQUIRED, List.of());
        }
        // Сравнение по .ordinal() хрупко к порядку объявления enum, поэтому дополнительно
        // проверяем совпадение пола явно, а не полагаемся на то, что мужская/женская шкалы
        // случайно далеко друг от друга.
        if (playerLevel.getGeneroAplicable() != categoriaNivel.getGeneroAplicable()
                || Math.abs(playerLevel.ordinal() - categoriaNivel.ordinal()) > MAX_STEP_DIFFERENCE) {
            return new Result(Verdict.LEVEL_MISMATCH, allowedLevelsAround(playerLevel));
        }
        return Result.allowed();
    }

    // Общая точка для TournamentService / DoubleTournamentRegistrationService — собирает
    // локализованное сообщение (MessageSource/LocaleContextHolder, как в PlayerService)
    // и бросает нужное исключение, не дублируя этот switch в каждом сервисе (DRY).
    public static void assertEligible(TournamentType tipo, Nivel categoriaNivel, Nivel playerLevel,
                                       MessageSource messageSource, Locale locale) {
        Result result = evaluate(tipo, categoriaNivel, playerLevel);
        switch (result.verdict()) {
            case LEVEL_REQUIRED -> throw new PlayerLevelRequiredException(
                    messageSource.getMessage("tournament.register.level_required", null, locale));
            case LEVEL_MISMATCH -> {
                String allowed = result.allowedLevels().stream()
                        .map(Nivel::getDisplay)
                        .collect(Collectors.joining(", "));
                throw new TournamentRegistrationException(messageSource.getMessage(
                        "tournament.register.level_mismatch",
                        new Object[]{playerLevel.getDisplay(), categoriaNivel.getDisplay(), allowed},
                        locale));
            }
            case ALLOWED -> { /* регистрация продолжается */ }
        }
    }

    private static List<Nivel> allowedLevelsAround(Nivel playerLevel) {
        Nivel[] values = Nivel.values();
        return IntStream.rangeClosed(
                        playerLevel.ordinal() - MAX_STEP_DIFFERENCE,
                        playerLevel.ordinal() + MAX_STEP_DIFFERENCE)
                .filter(ordinal -> ordinal >= 0 && ordinal < values.length)
                .mapToObj(ordinal -> values[ordinal])
                .filter(Nivel::isPlayerLevel)
                .toList();
    }
}
