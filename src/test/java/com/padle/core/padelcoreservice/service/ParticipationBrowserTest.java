package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@EnabledIfSystemProperty(named = "participation.browser", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "management.health.mail.enabled=false",
        "app.participation.response-email.enabled=true",
        "app.base-url=http://localhost:8080",
        "app.analytics.meta.enabled=false", "app.analytics.meta.access-token=",
        "app.analytics.ga4-id=", "app.analytics.clarity-id=",
        "disposable-email-domains.checkers.daily-updated-domains=false",
        "disposable-email-domains.checkers.reload.enabled=false",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test"
})
class ParticipationBrowserTest {
    @LocalServerPort int port;
    @Autowired PlayerRepository players;
    @Autowired TournamentRepository tournaments;
    @Autowired ClubRepository clubs;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired ParticipationRequestRepository participation;
    @Autowired TournamentService service;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @MockitoBean JavaMailSender mail;

    record Fixture(String baseUrl, String email, String strangerEmail, String password, long eventId,
                   long bookerId, long guestId, long queuedId) {}

    @Test
    void promotedBookerUsesEmailAndCabinetWithoutReleasingPlaceUntilBothDecline() throws Exception {
        var messages = new LinkedBlockingQueue<MimeMessage>();
        when(mail.createMimeMessage()).thenAnswer(call -> new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(call -> { messages.add(call.getArgument(0)); return null; }).when(mail).send(any(MimeMessage.class));

        var fixture = new TransactionTemplate(transactions).execute(status -> seed());
        for (long id : new long[] {fixture.bookerId(), fixture.guestId()}) {
            var row = registrations.findByTournamentIdAndPlayerId(fixture.eventId(), id).orElseThrow();
            assertThat(row.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
            assertThat(participation.findByRegistrationIdAndSupersededAtIsNull(row.getId())).isPresent();
        }
        var link = Pattern.compile("/participacion/[0-9a-f-]{36}");
        String bookerPath = "";
        String guestPath = "";
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while ((bookerPath.isBlank() || guestPath.isBlank()) && System.nanoTime() < end) {
            var message = messages.poll(1, TimeUnit.SECONDS);
            if (message == null) continue;
            message.saveChanges();
            String html = htmlBody(message);
            var match = link.matcher(html);
            if (!match.find()) continue;
            assertThat(message.getAllRecipients()[0].toString()).isEqualTo(fixture.email());
            if (html.contains("compañero invitado")) guestPath = match.group();
            else bookerPath = match.group();
        }
        assertThat(bookerPath).isNotBlank();
        assertThat(guestPath).isNotBlank().isNotEqualTo(bookerPath);

        var input = Files.createTempFile("padel-participation-browser-", ".json");
        try {
            Files.writeString(input, json.writeValueAsString(new BrowserInput(fixture, bookerPath, guestPath)));
            var browser = new ProcessBuilder("node", "tests/browser/participation.browser.mjs", input.toString())
                    .inheritIO().start();
            boolean finished = browser.waitFor(90, TimeUnit.SECONDS);
            if (!finished) browser.destroyForcibly();
            assertThat(finished).isTrue();
            assertThat(browser.exitValue()).isZero();
        } finally {
            Files.deleteIfExists(input);
        }

        assertThat(registrations.findByTournamentIdAndPlayerId(fixture.eventId(), fixture.bookerId()).orElseThrow()
                .getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(registrations.findByTournamentIdAndPlayerId(fixture.eventId(), fixture.guestId()).orElseThrow()
                .getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(registrations.findByTournamentIdAndPlayerId(fixture.eventId(), fixture.queuedId()).orElseThrow()
                .getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
    }

    private record BrowserInput(Fixture fixture, String bookerPath, String guestPath) {}

    private Fixture seed() {
        String suffix = UUID.randomUUID().toString();
        String password = "Local-" + suffix;
        String hash = passwords.encode(password);
        var club = clubs.saveAndFlush(Club.builder().nombre("Club QA " + suffix).isActive(true).build());
        var event = tournaments.saveAndFlush(Tournament.builder().nombre("Americano QA reserva").clubId(club.getId())
                .fechaInicio(LocalDate.now().plusDays(3)).horaInicio(LocalTime.NOON).tipo(TournamentType.AMERICANO)
                .modalidad(Modalidad.DOBLES).generoFormato(GenderFormat.MIXTO).categoriaNivel(Nivel.C9)
                .cupoMax(1).precio(BigDecimal.ZERO).moneda("ARS").estado(TournamentStatus.REGISTRO_ABIERTO)
                .contactoOrganizador("qa@example.test").build());
        var first = player("Primero", suffix, hash, false);
        var second = player("Segundo", suffix, hash, false);
        var booker = player("Titular", suffix, hash, false);
        var guest = player("Invitado", suffix, hash, true);
        var queued = player("Reserva", suffix, hash, false);
        var queuedPartner = player("Siguiente", suffix, hash, false);
        var stranger = player("Ajeno", suffix, hash, false);
        pair(event, first, second, RegistrationStatus.CONFIRMED, 1);
        pair(event, booker, guest, RegistrationStatus.WAITLIST, 1);
        pair(event, queued, queuedPartner, RegistrationStatus.WAITLIST, 2);
        service.cancelPairRegistration(event.getId(), first.getId(), "QA");
        return new Fixture("http://127.0.0.1:" + port, booker.getEmail(), stranger.getEmail(), password, event.getId(),
                booker.getId(), guest.getId(), queued.getId());
    }

    private PlayerPadel player(String name, String suffix, String hash, boolean guest) {
        return players.saveAndFlush(PlayerPadel.builder().nombre(name).apellido("QA")
                .email(name.toLowerCase(java.util.Locale.ROOT) + "-" + suffix + (guest ? "@1padel.guest" : "@example.test"))
                .passwordHash(hash).activo(true).emailConfirmado(true).preferredLocale("es").nivelJugador(Nivel.C9).build());
    }

    private void pair(Tournament event, PlayerPadel first, PlayerPadel second, RegistrationStatus status, int position) {
        UUID booking = UUID.randomUUID();
        registrations.saveAndFlush(row(event, first, second, first.getId(), booking, status, position));
        registrations.saveAndFlush(row(event, second, first, first.getId(), booking, status, position));
    }

    private TournamentRegistration row(Tournament event, PlayerPadel player, PlayerPadel partner, long booker,
                                       UUID booking, RegistrationStatus status, int position) {
        return TournamentRegistration.builder().tournament(event).player(player).partner(partner).isDoubleRegistration(true)
                .mainPlayerId(booker).pairBookedBy(booker).pairBookingId(booking).status(status)
                .position(status == RegistrationStatus.CONFIRMED ? position : null)
                .waitlistPosition(status == RegistrationStatus.WAITLIST ? position : null).build();
    }

    private static String htmlBody(Part part) throws Exception {
        if (part.isMimeType("text/html")) return (String) part.getContent();
        if (part.getContent() instanceof MimeMultipart multipart) return htmlBody(multipart.getBodyPart(0));
        return "";
    }
}
