package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LFPT-443: email-напоминание за 5ч до начала индивидуального турнира (AMERICANO/KING_OF_COURT)
 * и Cancha Abierta. Проверяет персистентную защиту от дублей (Tournament.startReminderSentAt)
 * и то, что текст письма меняется в зависимости от tipo — по образцу
 * PairTournamentReminderSchedulerTest (LFPT-437).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key",
        "recaptcha.secret-key=test-secret-key",
})
@Transactional
class IndividualTournamentReminderSchedulerTest {

    @Autowired
    private IndividualTournamentReminderScheduler scheduler;
    @Autowired
    private TournamentRepository tournamentRepository;
    @Autowired
    private TournamentRegistrationRepository registrationRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private SpringTemplateEngine templateEngine;

    @Test
    void torneoAmericano_conJugadorConfirmado_enVentanaDe5h_seMarcaComoEnviado() {
        Club club = createClub();
        Tournament tournament = createTournament(club.getId(), TournamentType.AMERICANO, LocalDateTime.now().plusHours(5));
        PlayerPadel player = createPlayer();
        createConfirmedRegistration(tournament, player);

        scheduler.sendIndividualTournamentReminders();

        Tournament reloaded = tournamentRepository.findById(tournament.getId()).orElseThrow();
        assertThat(reloaded.getStartReminderSentAt()).isNotNull();
    }

    @Test
    void torneoKingOfCourt_enVentanaDe5h_seMarcaComoEnviado() {
        Club club = createClub();
        Tournament tournament = createTournament(club.getId(), TournamentType.KING_OF_COURT, LocalDateTime.now().plusHours(5));
        PlayerPadel player = createPlayer();
        createConfirmedRegistration(tournament, player);

        scheduler.sendIndividualTournamentReminders();

        Tournament reloaded = tournamentRepository.findById(tournament.getId()).orElseThrow();
        assertThat(reloaded.getStartReminderSentAt()).isNotNull();
    }

    @Test
    void torneoCanchaAbierta_enVentanaDe5h_seMarcaComoEnviado() {
        Club club = createClub();
        Tournament tournament = createTournament(club.getId(), TournamentType.CANCHA_ABIERTA, LocalDateTime.now().plusHours(5));
        PlayerPadel player = createPlayer();
        createConfirmedRegistration(tournament, player);

        scheduler.sendIndividualTournamentReminders();

        Tournament reloaded = tournamentRepository.findById(tournament.getId()).orElseThrow();
        assertThat(reloaded.getStartReminderSentAt()).isNotNull();
    }

    @Test
    void torneoAmericanoTeams_enVentanaDe5h_noSeMarca() {
        Club club = createClub();
        Tournament tournament = createTournament(club.getId(), TournamentType.AMERICANO_TEAMS, LocalDateTime.now().plusHours(5));
        PlayerPadel player = createPlayer();
        createConfirmedRegistration(tournament, player);

        scheduler.sendIndividualTournamentReminders();

        Tournament reloaded = tournamentRepository.findById(tournament.getId()).orElseThrow();
        assertThat(reloaded.getStartReminderSentAt()).isNull();
    }

    @Test
    void torneoIndividual_fueraDeLaVentana_noSeMarca() {
        Club club = createClub();
        Tournament tournamentPronto = createTournament(club.getId(), TournamentType.AMERICANO, LocalDateTime.now().plusHours(1));
        Tournament tournamentLejos = createTournament(club.getId(), TournamentType.KING_OF_COURT, LocalDateTime.now().plusHours(10));

        scheduler.sendIndividualTournamentReminders();

        assertThat(tournamentRepository.findById(tournamentPronto.getId()).orElseThrow().getStartReminderSentAt()).isNull();
        assertThat(tournamentRepository.findById(tournamentLejos.getId()).orElseThrow().getStartReminderSentAt()).isNull();
    }

    @Test
    void torneoYaMarcado_noSeReenviaAunqueSigaEnVentana() {
        Club club = createClub();
        Tournament tournament = createTournament(club.getId(), TournamentType.AMERICANO, LocalDateTime.now().plusHours(5));
        LocalDateTime yaEnviadoEn = LocalDateTime.now().minusDays(1);
        tournament.setStartReminderSentAt(yaEnviadoEn);
        tournamentRepository.save(tournament);

        scheduler.sendIndividualTournamentReminders();

        Tournament reloaded = tournamentRepository.findById(tournament.getId()).orElseThrow();
        assertThat(reloaded.getStartReminderSentAt()).isEqualToIgnoringNanos(yaEnviadoEn);
    }

    @Test
    void torneoYaMarcado_yLuegoEditado_noSeReenvia() {
        Club club = createClub();
        Tournament tournament = createTournament(club.getId(), TournamentType.KING_OF_COURT, LocalDateTime.now().plusHours(5));
        LocalDateTime yaEnviadoEn = LocalDateTime.now().minusDays(1);
        tournament.setStartReminderSentAt(yaEnviadoEn);
        tournamentRepository.save(tournament);

        // Simula edición del torneo (cambia hora de inicio) después del primer envío,
        // pero sigue dentro de la ventana de 5h.
        LocalDateTime nuevoInicio = LocalDateTime.now().plusHours(5).plusMinutes(5);
        tournament.setFechaInicio(nuevoInicio.toLocalDate());
        tournament.setHoraInicio(nuevoInicio.toLocalTime());
        tournamentRepository.save(tournament);

        scheduler.sendIndividualTournamentReminders();

        Tournament reloaded = tournamentRepository.findById(tournament.getId()).orElseThrow();
        assertThat(reloaded.getStartReminderSentAt()).isEqualToIgnoringNanos(yaEnviadoEn);
    }

    @Test
    void plantillaIndividual_contieneAdvertenciaDePenalizacion() {
        Context context = new Context(new java.util.Locale("es"));
        context.setVariable("playerName", "Juan");
        context.setVariable("clubName", "Club Padel Central");
        context.setVariable("direccion", "Av. Siempre Viva 742");
        context.setVariable("hora", "18:30");
        context.setVariable("year", 2026);

        String html = templateEngine.process("email/individual-tournament-reminder", context);

        assertThat(html).contains("Club Padel Central", "Av. Siempre Viva 742", "18:30",
                "Te pedimos llegar 15 minutos antes", "−10 puntos");
    }

    @Test
    void plantillaCanchaAbierta_noContieneAdvertenciaDePenalizacion() {
        Context context = new Context(new java.util.Locale("es"));
        context.setVariable("playerName", "Juan");
        context.setVariable("clubName", "Club Padel Central");
        context.setVariable("direccion", "Av. Siempre Viva 742");
        context.setVariable("hora", "18:30");
        context.setVariable("year", 2026);

        String html = templateEngine.process("email/cancha-abierta-reminder", context);

        assertThat(html).contains("Club Padel Central", "Av. Siempre Viva 742", "18:30",
                "avisá al organizador");
        assertThat(html).doesNotContain("puntos", "penaliza");
    }

    private Club createClub() {
        return clubRepository.save(Club.builder()
                .nombre("Club " + UUID.randomUUID())
                .direccion("Av. Test 123")
                .isActive(true)
                .build());
    }

    private PlayerPadel createPlayer() {
        String suffix = "LFPT443-" + UUID.randomUUID();
        return playerRepository.save(PlayerPadel.builder()
                .nombre("Test")
                .apellido("Player")
                .email(suffix + "@example.com")
                .passwordHash("$2b$12$test")
                .activo(true)
                .emailConfirmado(true)
                .oauth2User(false)
                .preferredLocale("es")
                .nivelJugador(Nivel.C9)
                .build());
    }

    private Tournament createTournament(Long clubId, TournamentType tipo, LocalDateTime inicio) {
        String suffix = "LFPT443-" + UUID.randomUUID();
        Modalidad modalidad = tipo == TournamentType.AMERICANO_TEAMS ? Modalidad.DOBLES : Modalidad.INDIVIDUAL;
        return tournamentRepository.save(Tournament.builder()
                .clubId(clubId)
                .nombre("Torneo " + suffix)
                .fechaInicio(inicio.toLocalDate())
                .horaInicio(inicio.toLocalTime())
                .generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.C7)
                .tipo(tipo)
                .modalidad(modalidad)
                .cupoMax(8)
                .precio(BigDecimal.ZERO)
                .estado(TournamentStatus.REGISTRO_ABIERTO)
                .contactoOrganizador("test@example.com")
                .isActive(true)
                .build());
    }

    private TournamentRegistration createConfirmedRegistration(Tournament tournament, PlayerPadel player) {
        return registrationRepository.save(TournamentRegistration.builder()
                .tournament(tournament)
                .player(player)
                .status(RegistrationStatus.CONFIRMED)
                .build());
    }
}
