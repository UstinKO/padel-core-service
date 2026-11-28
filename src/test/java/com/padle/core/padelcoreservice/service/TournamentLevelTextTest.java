package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.util.TournamentLevelText;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.util.Locale;
import static org.assertj.core.api.Assertions.assertThat;

class TournamentLevelTextTest {
    @ParameterizedTest
    @CsvSource({"ru,Все уровни", "es,Todos los niveles", "en,All levels"})
    void labelsOnlyTheReservedLevelToken(String language, String label) {
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        var locale = Locale.forLanguageTag(language);
        assertThat(TournamentLevelText.eventName("Masculino · TODOS", messages, locale))
                .isEqualTo("Masculino · " + label);
        assertThat(TournamentLevelText.eventName("Club TODOSUNIDOS C7", messages, locale))
                .isEqualTo("Club TODOSUNIDOS C7");
        assertThat(TournamentLevelText.eventName(null, messages, locale)).isNull();
    }
}
