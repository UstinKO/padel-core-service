package com.padle.core.padelcoreservice.marketing;

import com.padle.core.padelcoreservice.model.*;
import com.padle.core.padelcoreservice.security.oauth2.CustomOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import static org.mockito.Mockito.mock;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@SpringBootTest(properties = {
        "app.analytics.meta.enabled=false",
        "disposable-email-domains.checkers.daily-updated-domains=false",
        "disposable-email-domains.checkers.reload.enabled=false",
        "spring.mail.username=", "spring.mail.password=",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test"
})
@Transactional
@AutoConfigureMockMvc
class InternalAttributionIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired MessageSource messages;
    @Autowired InternalAttributionService attribution;
    @Autowired AttributionRegistrationJournal journal;
    @Autowired RegistrationSourceReportService reports;
    @Autowired MarketingController controller;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PlayerRepository players;
    @Autowired ClubRepository clubs;
    @Autowired TournamentRepository tournaments;
    @Autowired TournamentRegistrationRepository registrations;

    @AfterEach
    void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void internalCaptureWorksWithoutMetaAndDirectVisitPreservesFirst() {
        assertThat(controller.config().getBody().enabled()).isFalse();
        assertThat(controller.config().getBody().attributionEnabled()).isTrue();
        var browser = browser();
        UUID visitor = capture(browser, "reby");
        browser.setCookies(new Cookie("cookieConsent", "accepted"), new Cookie(InternalAttributionService.COOKIE, visitor.toString()));
        capture(browser, "instagram");
        attribution.capture(new MarketingContextService.Capture(null, null, "/", null), browser, new MockHttpServletResponse());
        assertThat(text("first_touch", visitor)).contains("reby").doesNotContain("instagram");
        assertThat(text("last_touch", visitor)).contains("instagram");
        assertThat(jdbc.queryForObject("SELECT expires_at <= captured_at + INTERVAL '180 days' FROM marketing_attribution_visitors_db WHERE id = ?", Boolean.class, visitor)).isTrue();
    }

    @Test
    void firstSourceBindsOnlyToOwnAccountAndDoesNotExtendOnReturn() {
        var player = player();
        login(player);
        var browser = browser();
        UUID visitor = capture(browser, "reby");
        var expiry = jdbc.queryForObject("SELECT expires_at FROM marketing_acquisition_db WHERE player_id = ?", Timestamp.class, player.getId());
        browser.setCookies(new Cookie("cookieConsent", "accepted"), new Cookie(InternalAttributionService.COOKIE, visitor.toString()));
        capture(browser, "instagram");
        assertThat(jdbc.queryForObject("SELECT first_touch FROM marketing_acquisition_db WHERE player_id = ?", String.class, player.getId())).contains("reby");
        assertThat(jdbc.queryForObject("SELECT expires_at FROM marketing_acquisition_db WHERE player_id = ?", Timestamp.class, player.getId())).isEqualTo(expiry);
        login(player());
        assertThat(attribution.visitorId(browser)).isEmpty();
    }

    @Test
    void googlePrincipalBindsTheSamePlayerAccount() {
        var player = player();
        var principal = new CustomOAuth2User(mock(OAuth2User.class), player);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, "", principal.getAuthorities()));
        capture(browser(), "reby");
        assertThat(jdbc.queryForObject("SELECT first_touch FROM marketing_acquisition_db WHERE player_id = ?",
                String.class, player.getId())).contains("reby");
    }

    @Test
    void registrationWindowUsesCaptureTimeAndPartnerDoesNotInheritAcquisition() {
        var player = player();
        var partner = player();
        login(player);
        var browser = browser();
        UUID visitor = capture(browser, "reby");
        var event = tournament();
        var own = registration(event, player);
        var other = registration(event, partner);
        Instant at = Instant.now();
        String old = touch("reby", at.minusSeconds(120L * 86400));
        jdbc.update("UPDATE marketing_attribution_visitors_db SET last_touch = ? WHERE id = ?", old, visitor);
        snapshot(own.getId(), player.getId(), visitor, at);
        snapshot(other.getId(), player.getId(), visitor, at);
        assertThat(jdbc.queryForObject("SELECT acquisition_touch FROM marketing_registrations_db WHERE registration_id = ?", String.class, own.getId())).contains("reby");
        assertThat(jdbc.queryForObject("SELECT acquisition_touch IS NULL AND registration_touch IS NULL FROM marketing_registrations_db WHERE registration_id = ?", Boolean.class, other.getId())).isTrue();
        var boundary = registration(event, player());
        jdbc.update("UPDATE marketing_attribution_visitors_db SET last_touch = ? WHERE id = ?", touch("instagram", at.minusSeconds(90L * 86400)), visitor);
        snapshot(boundary.getId(), player.getId(), visitor, at);
        assertThat(jdbc.queryForObject("SELECT registration_touch FROM marketing_registrations_db WHERE registration_id = ?", String.class, boundary.getId())).contains("instagram");
        jdbc.update("UPDATE marketing_attribution_visitors_db SET last_touch = ? WHERE id = ?", touch("telegram", at), visitor);
        snapshot(boundary.getId(), player.getId(), visitor, at.plusSeconds(1));
        assertThat(jdbc.queryForObject("SELECT registration_touch FROM marketing_registrations_db WHERE registration_id = ?", String.class, boundary.getId())).contains("instagram").doesNotContain("telegram");
    }

    @Test
    void revokeAndExpiryClearAccountAndSnapshotCopies() {
        var player = player();
        login(player);
        var browser = browser();
        UUID visitor = capture(browser, "reby");
        var registration = registration(tournament(), player);
        snapshot(registration.getId(), player.getId(), visitor, Instant.now());
        browser.setCookies(new Cookie("cookieConsent", "accepted"), new Cookie(InternalAttributionService.COOKIE, visitor.toString()));
        attribution.revoke(browser, new MockHttpServletResponse());
        assertThat(jdbc.queryForObject("SELECT acquisition_touch IS NULL AND registration_touch IS NULL FROM marketing_registrations_db WHERE registration_id = ?", Boolean.class, registration.getId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT first_touch IS NULL FROM marketing_acquisition_db WHERE player_id = ?", Boolean.class, player.getId())).isTrue();
        capture(browser(), "instagram");
        assertThat(jdbc.queryForObject("SELECT first_touch IS NULL FROM marketing_acquisition_db WHERE player_id = ?", Boolean.class, player.getId())).isTrue();
        jdbc.update("UPDATE marketing_acquisition_db SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE player_id = ?", player.getId());
        attribution.expire();
        assertThat(attribution.visitorId(browser)).isEmpty();
    }

    @Test
    void reportCountsTwoParticipantsOnePairAndOnlyOneFirstRegistrationPerPlayer() {
        var event = tournament();
        var a = player();
        var b = player();
        var first = registration(event, a);
        var second = registration(event, b);
        jdbc.update("UPDATE tournament_registrations_db SET partner_id = ?, main_player_id = ?, is_double_registration = true WHERE id = ?", b.getId(), a.getId(), first.getId());
        jdbc.update("UPDATE tournament_registrations_db SET partner_id = ?, main_player_id = ?, is_double_registration = true WHERE id = ?", a.getId(), a.getId(), second.getId());
        registration(tournament(), a);
        var report = reports.report(filter(event.getId()));
        assertThat(report.totals().participants()).isEqualTo(2);
        assertThat(report.totals().pairs()).isEqualTo(1);
        assertThat(report.totals().firstRegistrations()).isEqualTo(2);
        assertThat(report.rows()).hasSize(1);
        assertThat(report.rows().get(0).source()).isEqualTo("unknown");
        assertThat(report.totals().confirmed()).isEqualTo(2);
        assertThat(report.totals().pairConfirmed()).isEqualTo(1);
        assertThat(report.measuredParticipants()).isZero();
    }

    @Test
    void reportUsesActualOpenFormatAndPreservesStoredEventName() {
        var event = tournament();
        registration(event, player());
        var originalLocale = LocaleContextHolder.getLocale();
        try {
            for (var language : new String[]{"es", "ru", "en"}) {
                var locale = Locale.forLanguageTag(language);
                LocaleContextHolder.setLocale(locale);
                for (var type : new String[]{"CANCHA_ABIERTA", "PADEL_CLINIC"}) {
                    jdbc.update("UPDATE tournaments_db SET tipo = ?, nombre = ? WHERE id = ?",
                            type, "Mixto · TODOS", event.getId());
                    var key = type.equals("CANCHA_ABIERTA") ? "cancha_abierta" : "padel_clinic";
                    var report = reports.report(filter(event.getId()));
                    assertThat(report.rows()).hasSize(1);
                    assertThat(report.rows().get(0).tournament()).isEqualTo(messages.getMessage(
                            "admin.tournaments.form.field.type." + key, null, locale));
                    assertThat(report.totals().participants()).isEqualTo(1);
                    assertThat(jdbc.queryForObject("SELECT nombre FROM tournaments_db WHERE id = ?",
                            String.class, event.getId())).isEqualTo("Mixto · TODOS");
                }
            }
            jdbc.update("UPDATE tournaments_db SET tipo = ?, nombre = ? WHERE id = ?",
                    "AMERICANO_TEAMS", "Masculino · C9", event.getId());
            assertThat(reports.report(filter(event.getId())).rows().get(0).tournament())
                    .isEqualTo("Masculino · C9");
        } finally {
            LocaleContextHolder.setLocale(originalLocale);
        }
    }

    @Test
    void reportRejectsInvalidPeriodAndFilters() {
        var filter = new RegistrationSourceReportService.Filter(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2),
                "last_non_direct", "", "", "", "", "", "", null, null, 0, 50);
        assertThatThrownBy(() -> reports.report(filter)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void reportRequiresSuperAdminAtHttpBoundary() throws Exception {
        mvc.perform(get("/api/admin/marketing/registration-sources"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/marketing/registration-sources")
                .with(user("player").roles("PLAYER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/marketing/registration-sources")
                .with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void futureAndOutsideWindowTouchesAreNotAccepted() {
        var actor = player();
        login(actor);
        UUID visitor = capture(browser(), "reby");
        Instant at = Instant.now();
        for (long offset : new long[]{-90L * 86400 - 1, 1}) {
            var registration = registration(tournament(), player());
            jdbc.update("UPDATE marketing_attribution_visitors_db SET last_touch = ? WHERE id = ?", touch("instagram", at.plusSeconds(offset)), visitor);
            snapshot(registration.getId(), actor.getId(), visitor, at);
            assertThat(jdbc.queryForObject("SELECT registration_touch IS NULL FROM marketing_registrations_db WHERE registration_id = ?", Boolean.class, registration.getId())).isTrue();
        }
    }

    @Test
    void expiredSourcesStayUnknownAndDoNotExtendFromVisits() {
        var actor = player();
        login(actor);
        UUID visitor = capture(browser(), "reby");
        assertThat(jdbc.queryForObject("SELECT expires_at = bound_at + INTERVAL '12 months' FROM marketing_acquisition_db WHERE player_id = ?", Boolean.class, actor.getId())).isTrue();
        jdbc.update("UPDATE marketing_acquisition_db SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE player_id = ?", actor.getId());
        jdbc.update("UPDATE marketing_attribution_visitors_db SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = ?", visitor);
        attribution.expire();
        capture(browser(), "instagram");
        assertThat(jdbc.queryForObject("SELECT first_touch IS NULL FROM marketing_acquisition_db WHERE player_id = ?", Boolean.class, actor.getId())).isTrue();
        assertThat(text("first_touch", visitor)).isNull();
    }

    private RegistrationSourceReportService.Filter filter(Long event) {
        return new RegistrationSourceReportService.Filter(null, null, "last_non_direct", "", "", "", "", "", "", event, null, 0, 50);
    }

    private void snapshot(Long id, Long actor, UUID visitor, Instant at) {
        ReflectionTestUtils.invokeMethod(journal, "snapshot", id, actor, visitor, at);
    }

    private String touch(String source, Instant at) {
        return json.writeValueAsString(new MarketingContextService.Touch(new MarketingContextService.Utm(source, "qr", "offline_tournaments", "", ""), at));
    }

    private String text(String column, UUID id) {
        assertThat(column).isIn("first_touch", "last_touch");
        return jdbc.queryForObject("SELECT " + column + " FROM marketing_attribution_visitors_db WHERE id = ?", String.class, id);
    }

    private UUID capture(MockHttpServletRequest request, String source) {
        var response = new MockHttpServletResponse();
        attribution.capture(new MarketingContextService.Capture(new MarketingContextService.Utm(source, "qr", "offline_tournaments", "", ""), null, "/", null), request, response);
        return UUID.fromString(response.getHeader("Set-Cookie").split(";", 2)[0].split("=", 2)[1]);
    }

    private MockHttpServletRequest browser() {
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie("cookieConsent", "accepted"));
        return request;
    }

    private void login(PlayerPadel player) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(player, "", player.getAuthorities()));
    }

    private TournamentRegistration registration(Tournament event, PlayerPadel player) {
        return registrations.saveAndFlush(TournamentRegistration.builder().tournament(event).player(player).status(RegistrationStatus.CONFIRMED).build());
    }

    private PlayerPadel player() {
        return players.saveAndFlush(PlayerPadel.builder().nombre("Тест").apellido("Источники")
                .email(UUID.randomUUID() + "@example.test").passwordHash("test-hash").activo(true)
                .emailConfirmado(true).preferredLocale("es").nivelJugador(Nivel.C9).build());
    }

    private Tournament tournament() {
        var club = clubs.saveAndFlush(Club.builder().nombre("Клуб " + UUID.randomUUID()).isActive(true).build());
        return tournaments.saveAndFlush(Tournament.builder().nombre("Событие теста").clubId(club.getId())
                .fechaInicio(LocalDate.now().plusDays(7)).horaInicio(LocalTime.NOON).tipo(TournamentType.AMERICANO)
                .modalidad(Modalidad.INDIVIDUAL).generoFormato(GenderFormat.MIXTO).categoriaNivel(Nivel.TODOS)
                .cupoMax(16).precio(BigDecimal.ZERO).moneda("ARS").estado(TournamentStatus.REGISTRO_ABIERTO)
                .contactoOrganizador("test@example.test").build());
    }
}
