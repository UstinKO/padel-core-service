package com.padle.core.padelcoreservice.service.calendar;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.service.TournamentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TournamentCalendarServiceTest {
    private final TournamentService tournaments = mock(TournamentService.class);
    private TournamentCalendarService service;
    private TournamentDto tournament;

    @BeforeEach
    void setup() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("i18n/messages");
        messages.setDefaultEncoding("UTF-8");
        service = new TournamentCalendarService(tournaments, messages);
        ReflectionTestUtils.setField(service, "baseUrl", "https://1-padel.com/?utm_source=private");
        tournament = TournamentDto.builder().id(42L).nombre("Мужской · C7").tipo(TournamentType.KING_OF_COURT)
                .categoriaNivel("C7").modalidad(Modalidad.INDIVIDUAL).fechaInicio(LocalDate.of(2026, 11, 2))
                .horaInicio(LocalTime.of(18, 30)).durationMinutes(90).clubNombre("Club Ñandú")
                .clubDireccion("Av. Córdoba 123").contactoOrganizador("private@example.com")
                .infoDetallada("Ваше участие подтверждено https://1-padel.com/admin/secret").build();
        when(tournaments.getActiveTournamentById(42L)).thenReturn(Optional.of(tournament));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ru", "es", "en"})
    void stableIdentityAndSafeReadOnlyDataInEveryLanguage(String language) {
        CalendarEvent event = service.getEvent(42L, Locale.forLanguageTag(language));
        assertThat(event.uid()).isEqualTo("tournament-42@1-padel.com");
        assertThat(event.title()).endsWith("C7 — 1-Padel").doesNotContain("Мужской");
        assertThat(event.startsAt()).isEqualTo(Instant.parse("2026-11-02T21:30:00Z"));
        assertThat(Duration.between(event.startsAt(), event.endsAt())).isEqualTo(Duration.ofMinutes(90));
        assertThat(event.location()).isEqualTo("Club Ñandú, Av. Córdoba 123");
        assertThat(event.url()).isEqualTo("https://1-padel.com/torneo/42");
        assertThat(event.description()).contains(event.url(), "15").doesNotContain("private", "/admin/", "подтверждено");
        verify(tournaments).getActiveTournamentById(42L);
        verifyNoMoreInteractions(tournaments);
    }

    @ParameterizedTest
    @EnumSource(TournamentType.class)
    void allFormatsSupportBothEntryModes(TournamentType type) {
        tournament.setTipo(type);
        for (Modalidad modality : Modalidad.values()) {
            tournament.setModalidad(modality);
            CalendarEvent event = service.getEvent(42L, Locale.forLanguageTag("ru"));
            assertThat(event.title()).endsWith(" — 1-Padel");
            if (type == TournamentType.CANCHA_ABIERTA || type == TournamentType.PADEL_CLINIC) {
                assertThat(event.title()).doesNotContain("C7", "Парное");
            } else if (modality == Modalidad.DOBLES) {
                assertThat(event.title()).contains("Парное участие");
            }
        }
    }

    @Test
    void rejectsMissingDurationAndInvalidZone() {
        tournament.setDurationMinutes(null);
        tournament.setDuracion("примерно несколько часов");
        assertThatThrownBy(() -> service.getEvent(42L, Locale.ENGLISH))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(422));
        tournament.setDurationMinutes(90);
        tournament.setTimeZone("unknown/zone");
        assertThatThrownBy(() -> service.getEvent(42L, Locale.ENGLISH)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void notFoundIsNotExposed() {
        assertThatThrownBy(() -> service.getEvent(999L, Locale.ENGLISH))
                .isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode().value()).isEqualTo(404));
    }

    @Test
    void honoursEventZoneAndLanguage() {
        tournament.setTimeZone("Europe/Madrid");
        assertThat(service.getEvent(42L, Locale.ENGLISH).startsAt()).isEqualTo(Instant.parse("2026-11-02T17:30:00Z"));
        assertThat(service.getEvent(42L, Locale.forLanguageTag("ru")).title()).isEqualTo("Король корта C7 — 1-Padel");
        assertThat(service.getEvent(42L, Locale.forLanguageTag("es")).title()).isEqualTo("Rey de Cancha C7 — 1-Padel");
        assertThat(service.getEvent(42L, Locale.ENGLISH).title()).isEqualTo("King of Court C7 — 1-Padel");
    }
}
