package com.padle.core.padelcoreservice.service;

import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.persistence.clubs.Club;
import com.padle.core.persistence.clubs.ClubRepository;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.persistence.identity.PlayerRepository;
import com.padle.core.padelcoreservice.dto.PartnerRegistrationDto;
import com.padle.core.padelcoreservice.exception.PlayerLevelRequiredException;
import com.padle.core.padelcoreservice.exception.PairRegistrationException;
import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.scheduling.enabled=false", "app.analytics.meta.enabled=false",
        "disposable-email-domains.checkers.daily-updated-domains=false",
        "disposable-email-domains.checkers.reload.enabled=false",
        "spring.mail.username=test", "spring.mail.password=test",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test"
})
@Transactional
class AllLevelsRegistrationIntegrationTest {
    @Autowired TournamentService individual;
    @Autowired DoubleTournamentRegistrationService doubles;
    @Autowired TournamentRepository events;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired PlayerRepository players;
    @Autowired ClubRepository clubs;
    @MockitoBean EmailService email;
    @MockitoBean TournamentNotificationService notifications;

    static Stream<Arguments> combinations() {
        return Arrays.stream(TournamentType.values()).flatMap(type -> Arrays.stream(GenderFormat.values())
                .map(gender -> Arguments.of(type, gender)));
    }

    @ParameterizedTest
    @MethodSource("combinations")
    void allLevelsKeepsNormalCapacityAndAcceptsDistantLevels(TournamentType type, GenderFormat gender) {
        var event = event(type, gender, Nivel.TODOS);
        var strong = player(Nivel.C3);
        var beginner = player(Nivel.D9);
        if (event.getModalidad() == Modalidad.DOBLES) {
            registerPair(event, strong, beginner);
            assertThat(registrations.findByTournamentIdAndPlayerId(event.getId(), beginner.getId()).orElseThrow().getStatus())
                    .isEqualTo(RegistrationStatus.CONFIRMED);
            var next = player(Nivel.C9);
            registerPair(event, next, player(Nivel.D6));
            assertThat(registrations.findByTournamentIdAndPlayerId(event.getId(), next.getId()).orElseThrow().getStatus())
                    .isEqualTo(RegistrationStatus.WAITLIST);
        } else {
            individual.registerPlayer(event.getId(), strong.getId());
            assertThat(registrations.findByTournamentIdAndPlayerId(event.getId(), strong.getId()).orElseThrow().getStatus())
                    .isEqualTo(RegistrationStatus.CONFIRMED);
            individual.registerPlayer(event.getId(), beginner.getId());
            assertThat(registrations.findByTournamentIdAndPlayerId(event.getId(), beginner.getId()).orElseThrow().getStatus())
                    .isEqualTo(RegistrationStatus.WAITLIST);
        }
        assertThat(events.findById(event.getId()).orElseThrow().getCategoriaNivel()).isEqualTo(Nivel.TODOS);
    }

    @Test
    void allLevelsRequiresTheMainAndExistingPartnerProfiles() {
        var event = event(TournamentType.AMERICANO_TEAMS, GenderFormat.FEMENINO, Nivel.TODOS);
        var missing = player(Nivel.SIN_ESPECIFICAR);
        var filled = player(Nivel.C3);
        assertThatThrownBy(() -> registerPair(event, missing, filled)).isInstanceOf(PlayerLevelRequiredException.class);
        assertThatThrownBy(() -> registerPair(event, filled, missing)).isInstanceOf(PairRegistrationException.class)
                .hasMessage("PARTNER_LEVEL_REQUIRED");
        assertThat(registrations.findByTournamentIdAndPlayerId(event.getId(), filled.getId())).isEmpty();
        var clinic = event(TournamentType.PADEL_CLINIC, GenderFormat.MIXTO, Nivel.TODOS);
        assertThatThrownBy(() -> individual.registerPlayer(clinic.getId(), missing.getId()))
                .isInstanceOf(PlayerLevelRequiredException.class);
    }

    @Test
    void specificAndMissingEventCategoriesDoNotBecomeAllLevels() {
        var player = player(Nivel.C3);
        var specific = event(TournamentType.KING_OF_COURT, GenderFormat.MASCULINO, Nivel.C9);
        assertThatThrownBy(() -> individual.registerPlayer(specific.getId(), player.getId()))
                .isInstanceOf(TournamentRegistrationException.class);
        var invalid = event(TournamentType.AMERICANO, GenderFormat.MIXTO, null);
        assertThatThrownBy(() -> individual.registerPlayer(invalid.getId(), player.getId()))
                .isInstanceOf(TournamentRegistrationException.class);
        assertThat(registrations.findByTournamentIdAndPlayerId(invalid.getId(), player.getId())).isEmpty();
    }

    private void registerPair(Tournament event, PlayerPadel first, PlayerPadel second) {
        var dto = individual.getTournamentDtoById(event.getId()).orElseThrow();
        doubles.registerForDoubleTournament(dto, first.getId(), PartnerRegistrationDto.builder()
                .existingUserId(second.getId()).build());
    }

    private Tournament event(TournamentType type, GenderFormat gender, Nivel level) {
        var club = clubs.saveAndFlush(Club.builder().nombre("All levels QA " + UUID.randomUUID()).isActive(true).build());
        return events.saveAndFlush(Tournament.builder().nombre("All levels QA").clubId(club.getId())
                .fechaInicio(LocalDate.now().plusDays(10)).horaInicio(LocalTime.NOON).generoFormato(gender)
                .categoriaNivel(level).tipo(type).modalidad(type == TournamentType.AMERICANO_TEAMS ? Modalidad.DOBLES : Modalidad.INDIVIDUAL)
                .cupoMax(1).precio(BigDecimal.ZERO).estado(TournamentStatus.REGISTRO_ABIERTO).isActive(true).build());
    }

    private PlayerPadel player(Nivel level) {
        String suffix = UUID.randomUUID().toString();
        return players.saveAndFlush(PlayerPadel.builder().nombre("All levels QA").apellido(suffix)
                .email(suffix + "@example.test").passwordHash("unused").activo(true).emailConfirmado(true)
                .preferredLocale("es").nivelJugador(level).telefono("+5491123456789").build());
    }
}
