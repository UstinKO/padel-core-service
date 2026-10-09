package com.padle.core.padelcoreservice.dto;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.format.support.DefaultFormattingConversionService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-384: th:field форматирует значение через ConversionService с учётом аннотаций поля.
 * Без @DateTimeFormat LocalDate/LocalTime выводились в коротком формате локали
 * ("12/1/26", "10:00 AM"), который HTML5 date/time/datetime-local не принимает —
 * браузер молча блокировал сабмит формы редактирования турнира.
 */
class TournamentDtoFormFormatTest {

    private final DefaultFormattingConversionService conversionService = new DefaultFormattingConversionService();

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"es", "ru", "en", "en-US"})
    void dateAndTimeFields_renderInHtml5Format_inAnyLocale(String languageTag) {
        LocaleContextHolder.setLocale(Locale.forLanguageTag(languageTag));

        assertThat(print("fechaInicio", LocalDate.of(2026, 12, 1))).isEqualTo("2026-12-01");
        assertThat(print("horaInicio", LocalTime.of(10, 0))).isEqualTo("10:00");
        assertThat(print("deadlineCancelacion", LocalDateTime.of(2026, 11, 30, 18, 30)))
                .isEqualTo("2026-11-30T18:30");
    }

    @ParameterizedTest
    @ValueSource(strings = {"es", "ru", "en", "en-US"})
    void html5Values_parseBack_inAnyLocale(String languageTag) {
        LocaleContextHolder.setLocale(Locale.forLanguageTag(languageTag));

        assertThat(parse("fechaInicio", "2026-12-01")).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(parse("horaInicio", "10:00")).isEqualTo(LocalTime.of(10, 0));
        assertThat(parse("deadlineCancelacion", "2026-11-30T18:30"))
                .isEqualTo(LocalDateTime.of(2026, 11, 30, 18, 30));
    }

    private String print(String field, Object value) {
        return (String) conversionService.convert(value, fieldType(field), TypeDescriptor.valueOf(String.class));
    }

    private Object parse(String field, String value) {
        return conversionService.convert(value, TypeDescriptor.valueOf(String.class), fieldType(field));
    }

    private static TypeDescriptor fieldType(String field) {
        try {
            return new TypeDescriptor(TournamentDto.class.getDeclaredField(field));
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(field, e);
        }
    }
}
