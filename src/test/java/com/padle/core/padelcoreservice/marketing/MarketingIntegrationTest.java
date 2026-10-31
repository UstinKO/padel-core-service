package com.padle.core.padelcoreservice.marketing;

import com.padle.core.padelcoreservice.dto.PaymentUpdateDto;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.padelcoreservice.service.PaymentService;
import com.padle.core.padelcoreservice.service.TournamentService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties = {
        "app.analytics.meta.enabled=true", "app.analytics.meta.pixel-id=930260823500064",
        "disposable-email-domains.checkers.daily-updated-domains=false",
        "disposable-email-domains.checkers.reload.enabled=false",
        "spring.mail.username=", "spring.mail.password=",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test"
})
@Transactional
@AutoConfigureMockMvc
class MarketingIntegrationTest {
    @Autowired MarketingContextService context;
    @Autowired MarketingOutbox outbox;
    @Autowired MarketingJournal journal;
    @Autowired MarketingReportService report;
    @Autowired PaymentService paymentService;
    @Autowired TournamentService tournamentService;
    @Autowired TournamentRepository tournaments;
    @Autowired PlayerRepository players;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired PaymentRepository payments;
    @Autowired ClubRepository clubs;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    private MockHttpServletRequest browser;
    private UUID visitor;

    @BeforeEach
    void consentedVisitor() {
        browser = new MockHttpServletRequest();
        browser.setRequestURI("/players/tournaments/1/register");
        browser.setMethod("POST");
        browser.setCookies(new Cookie("cookieConsent", "accepted"));
        var response = new MockHttpServletResponse();
        context.capture(new MarketingContextService.Capture(
                new MarketingContextService.Utm("meta", "paid_social", "campaign1", "ad1", "set1"),
                "real_click_id", "/", "fb.1.1700000000000.12345"), browser, response);
        String cookie = response.getHeader("Set-Cookie").split(";", 2)[0].split("=", 2)[1];
        visitor = UUID.fromString(cookie);
        browser.setCookies(new Cookie("cookieConsent", "accepted"), new Cookie("meta_visitor", cookie));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(browser));
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void savedRegistrationAndReserveHaveDifferentEventsAndRetriesDoNotDuplicate() {
        Tournament tournament = tournament(1);
        var confirmed = tournamentService.registerPlayer(tournament.getId(), player().getId());
        var reserve = tournamentService.registerPlayer(tournament.getId(), player().getId());
        entities.flush();
        assertThat(events(confirmed.getId())).containsExactly("EventRegistration");
        assertThat(events(reserve.getId())).containsExactly("EventWaitlist");
        var registration = registrations.findById(confirmed.getId()).orElseThrow();
        registration.setAttended(true);
        registrations.saveAndFlush(registration);
        assertThat(events(confirmed.getId())).containsExactly("EventRegistration");
        var pending = outbox.browserEvents(browser);
        assertThat(pending).hasSize(2);
        outbox.acknowledge(pending.get(0).eventId(), browser);
        assertThat(new MarketingOutbox(jdbc, new MetaProperties(true, "930260823500064", "", "v26.0", true, ""), context)
                .browserEvents(browser)).hasSize(1);
    }

    @Test
    void failedRegistrationDoesNotEmitAndAccountCreationDoesNotEmit() {
        PlayerPadel player = player();
        assertThat(outbox.browserEvents(browser)).isEmpty();
        assertThatThrownBy(() -> tournamentService.registerPlayer(-1L, player.getId())).isInstanceOf(RuntimeException.class);
        assertThat(outbox.browserEvents(browser)).isEmpty();
    }

    @Test
    void pendingPartnerIsNotConfirmedAndPromotionHasOneStableEvent() {
        var registration = registration(RegistrationStatus.SOLO_SEARCH);
        assertThat(events(registration.getId())).containsExactly("EventRegistrationPending");
        registration.setStatus(RegistrationStatus.WAITLIST);
        registrations.saveAndFlush(registration);
        registration.setStatus(RegistrationStatus.CONFIRMED);
        registrations.saveAndFlush(registration);
        registration.setStatus(RegistrationStatus.CANCELLED);
        registrations.saveAndFlush(registration);
        registration.setStatus(RegistrationStatus.CONFIRMED);
        registrations.saveAndFlush(registration);
        assertThat(events(registration.getId())).containsExactlyInAnyOrder("EventRegistrationPending", "EventWaitlist", "EventRegistration");
    }

    @Test
    void cumulativePartialAndFinalPaymentSendOnlyDeltasAndOnePaidParticipant() {
        var registration = registration(RegistrationStatus.CONFIRMED);
        Payment payment = payment(registration, "400", "ARS", null);
        assertThat(purchases(payment.getId())).containsExactly(new BigDecimal("400.00"));
        assertThat(paidParticipants(payment.getId())).isZero();
        payment.setAmount(new BigDecimal("1000"));
        payments.saveAndFlush(payment);
        assertThat(purchases(payment.getId())).containsExactly(new BigDecimal("400.00"), new BigDecimal("600.00"));
        assertThat(paidParticipants(payment.getId())).isEqualTo(1);
        payment.setNotes("Сохранено повторно");
        payments.saveAndFlush(payment);
        assertThat(purchases(payment.getId())).hasSize(2);
    }

    @Test
    void reducingAmountRefundAndRestoringItDoNotInventNewRevenue() {
        Payment payment = payment(registration(RegistrationStatus.CONFIRMED), "1000", "ARS", null);
        payment.setAmount(new BigDecimal("400"));
        payments.saveAndFlush(payment);
        assertThat(outbox.claim()).isEmpty();
        payment.setAmount(new BigDecimal("1000"));
        payment.setStatus(PaymentStatus.REFUNDED);
        payments.saveAndFlush(payment);
        payment.setStatus(PaymentStatus.PAID);
        payments.saveAndFlush(payment);
        assertThat(purchases(payment.getId())).hasSize(1);
    }

    @Test
    void zeroUnsupportedCurrencyAndUnpaidRowsDoNotCreatePurchase() {
        var registration = registration(RegistrationStatus.CONFIRMED);
        assertThat(purchases(payment(registration, "0", "ARS", null).getId())).isEmpty();
        assertThat(purchases(payment(registration, "200", "USDT", "PARTNER_PAYMENT").getId())).isEmpty();
        Payment pending = Payment.builder().registration(registration).amount(new BigDecimal("200"))
                .currency("USD").status(PaymentStatus.PENDING).build();
        payments.saveAndFlush(pending);
        assertThat(purchases(pending.getId())).isEmpty();
        pending.setStatus(PaymentStatus.PAID);
        payments.saveAndFlush(pending);
        assertThat(purchases(pending.getId())).containsExactly(new BigDecimal("200.00"));
        assertThat(paidParticipants(pending.getId())).isZero();
    }

    @Test
    void guestPairUsesTwoIndependentPaidSlots() {
        var registration = registration(RegistrationStatus.CONFIRMED);
        Payment main = payment(registration, "1000", "ARS", null);
        Payment partner = payment(registration, "1000", "ARS", "PARTNER_PAYMENT");
        assertThat(purchases(main.getId())).containsExactly(new BigDecimal("1000.00"));
        assertThat(purchases(partner.getId())).containsExactly(new BigDecimal("1000.00"));
        assertThat(paidParticipants(main.getId()) + paidParticipants(partner.getId())).isEqualTo(2);
    }

    @Test
    void deliveryRetryAndRestartPreserveEventIdAndOriginalTime() {
        Payment payment = payment(registration(RegistrationStatus.CONFIRMED), "1000", "ARS", null);
        var first = outbox.claim().orElseThrow();
        assertThat(outbox.claim()).isEmpty();
        outbox.finish(first, false, 503);
        jdbc.update("UPDATE marketing_events_db SET next_attempt_at = CURRENT_TIMESTAMP WHERE event_id = ?", first.eventId());
        var restarted = new MarketingOutbox(jdbc, new MetaProperties(true, "930260823500064", "", "v26.0", true, ""), context);
        var second = restarted.claim().orElseThrow();
        assertThat(second.eventId()).isEqualTo(first.eventId());
        assertThat(second.occurredAt()).isEqualTo(first.occurredAt());
        outbox.finish(second, true, 200);
        assertThat(outbox.claim()).isEmpty();
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void lostAcknowledgementLeaseCanBeRecoveredButExpiresInsideMetaDeduplicationWindow() {
        payment(registration(RegistrationStatus.CONFIRMED), "1000", "ARS", null);
        var first = outbox.claim().orElseThrow();
        jdbc.update("UPDATE marketing_events_db SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 minute' WHERE event_id = ?", first.eventId());
        assertThat(outbox.claim().orElseThrow().eventId()).isEqualTo(first.eventId());
        jdbc.update("UPDATE marketing_events_db SET occurred_at = CURRENT_TIMESTAMP - INTERVAL '48 hours' WHERE event_id = ?", first.eventId());
        assertThat(outbox.claim()).isEmpty();
    }

    @Test
    void revokedOrMissingConsentSuppressesBrowserAndServerTracking() {
        Payment payment = payment(registration(RegistrationStatus.CONFIRMED), "1000", "ARS", null);
        context.revoke(browser, new MockHttpServletResponse());
        assertThat(outbox.claim()).isEmpty();
        assertThat(outbox.browserEvents(browser)).isEmpty();
        assertThat(report.export(payment.getRegistration().getTournament().getId())).doesNotContain("campaign1", "real_click_id");
        browser.setCookies(new Cookie("cookieConsent", "rejected"));
        assertThat(events(registration(RegistrationStatus.CONFIRMED).getId())).isEmpty();
    }

    @Test
    void directVisitAndAuthenticationKeepSourceButNewCampaignOnlyChangesLastTouch() {
        var response = new MockHttpServletResponse();
        context.capture(new MarketingContextService.Capture(null, null, "/torneos", null), browser, response);
        context.capture(new MarketingContextService.Capture(
                new MarketingContextService.Utm("meta", "paid_social", "campaign2", "ad2", "set2"), null, "/", null), browser, response);
        browser.setUserPrincipal(() -> "participant@example.test");
        var registration = registration(RegistrationStatus.CONFIRMED);
        String csv = report.export(registration.getTournament().getId());
        assertThat(csv).contains("campaign1", "campaign2").doesNotContain("participant@example.test", "real_click_id");
        context.capture(new MarketingContextService.Capture(
                new MarketingContextService.Utm("meta", "paid_social", "campaign3", null, null), null, "/", null), browser, response);
        assertThat(report.export(registration.getTournament().getId())).doesNotContain("campaign3");
    }

    @Test
    void disabledIntegrationAdvancesBaselineWithoutBackfillingWhenEnabled() {
        var registration = registration(RegistrationStatus.CONFIRMED);
        Payment payment = payment(registration, "300", "ARS", null);
        jdbc.update("DELETE FROM marketing_events_db WHERE payment_id = ?", payment.getId());
        var disabled = new MarketingJournal(jdbc, new MetaProperties(false, "", "", "", true, ""), context);
        payment.setAmount(new BigDecimal("500"));
        disabled.payment(payment);
        entities.detach(payment);
        Payment reloaded = payments.findById(payment.getId()).orElseThrow();
        reloaded.setAmount(new BigDecimal("600"));
        payments.saveAndFlush(reloaded);
        assertThat(purchases(payment.getId())).containsExactly(new BigDecimal("100.00"));
    }

    @Test
    void duplicateManualFormWithoutPaymentIdReusesExistingPayment() {
        var registration = registration(RegistrationStatus.CONFIRMED);
        PaymentUpdateDto first = update(registration);
        paymentService.savePaymentManagementData(registration.getTournament().getId(), List.of(first), 1L);
        entities.flush();
        PaymentUpdateDto retry = update(registration);
        paymentService.savePaymentManagementData(registration.getTournament().getId(), List.of(retry), 1L);
        entities.flush();
        assertThat(payments.findByRegistrationId(registration.getId())).hasSize(1);
        assertThat(purchases(payments.findByRegistrationId(registration.getId()).get(0).getId())).hasSize(1);
    }

    @Test
    void publicCaptureRequiresCsrfAndMarketingConsent() throws Exception {
        mvc.perform(post("/api/marketing/context").contentType("application/json")
                .content("{\"path\":\"/\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/marketing/context").with(csrf()).contentType("application/json")
                .content("{\"path\":\"/\"}")).andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(get("/api/marketing/config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.pixelId").value("930260823500064"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void exportRejectsAnonymousPlayersAndOtherOwners() throws Exception {
        long tournamentId = tournament(10).getId();
        mvc.perform(get("/admin/marketing/export.csv").param("tournamentId", Long.toString(tournamentId)))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/marketing/export.csv").param("tournamentId", Long.toString(tournamentId))
                .with(user(player()))).andExpect(redirectedUrl("/error/403"));
        Owner other = Owner.builder().id(9999L).email("owner@example.test").password("test")
                .role(OwnerRole.ORGANIZER).isActive(true).build();
        mvc.perform(get("/admin/marketing/export.csv").param("tournamentId", Long.toString(tournamentId))
                .with(user(other))).andExpect(redirectedUrl("/error/403"));
        Owner administrator = Owner.builder().id(9998L).email("admin@example.test").password("test")
                .role(OwnerRole.SUPER_ADMIN).isActive(true).build();
        mvc.perform(get("/admin/marketing/export.csv").param("tournamentId", Long.toString(tournamentId))
                .with(user(administrator))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("registration_id,payment_id")));
    }

    @Test
    void organizerBrowserIsNeverBoundToParticipants() {
        browser.setRequestURI("/admin/tournaments/123/payments/save");
        var registration = registration(RegistrationStatus.CONFIRMED);
        Payment payment = payment(registration, "1000", "ARS", null);
        assertThat(events(registration.getId())).isEmpty();
        assertThat(purchases(payment.getId())).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rolledBackPaymentHasNoDeliverableEvent() {
        AtomicLong paymentId = new AtomicLong();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            paymentId.set(payment(registration(RegistrationStatus.CONFIRMED), "1000", "ARS", null).getId());
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM marketing_events_db WHERE payment_id = ?", Integer.class, paymentId.get())).isZero();
        assertThat(payments.findById(paymentId.get())).isEmpty();
        jdbc.update("DELETE FROM marketing_visitors_db WHERE id = ?", visitor);
    }

    @Test
    void currencyCorrectionRebasesWithoutCreatingFakeRevenue() {
        Payment payment = payment(registration(RegistrationStatus.CONFIRMED), "1000", "ARS", null);
        payment.setCurrency("USD");
        payment.setAmount(new BigDecimal("10"));
        payments.saveAndFlush(payment);
        assertThat(purchases(payment.getId())).hasSize(1);
        assertThat(outbox.claim()).isEmpty();
        payment.setAmount(new BigDecimal("15"));
        payments.saveAndFlush(payment);
        assertThat(purchases(payment.getId())).containsExactly(new BigDecimal("5.00"), new BigDecimal("1000.00"));
    }

    @Test
    void testModeEventsAreNeverReplayedIntoProductionMode() {
        payment(registration(RegistrationStatus.CONFIRMED), "1000", "ARS", null);
        var production = new MarketingOutbox(jdbc, new MetaProperties(true, "930260823500064", "", "v26.0", false, ""), context);
        assertThat(production.claim()).isEmpty();
        assertThat(production.browserEvents(browser)).isEmpty();
        assertThat(outbox.claim()).isPresent();
    }

    @Test
    void oldUnattributedRegistrationDoesNotAcquireSourceFromLaterPayment() {
        RequestContextHolder.resetRequestAttributes();
        var registration = registration(RegistrationStatus.CONFIRMED);
        Payment payment = payment(registration, "300", "ARS", null);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(browser));
        payment.setAmount(new BigDecimal("1000"));
        payments.saveAndFlush(payment);
        assertThat(purchases(payment.getId())).isEmpty();
        assertThat(report.export(registration.getTournament().getId())).doesNotContain("campaign1");
    }

    @Test
    void expiredVisitorCannotGenerateEventsAndAttributionIsCleared() {
        var registration = registration(RegistrationStatus.CONFIRMED);
        payment(registration, "1000", "ARS", null);
        jdbc.update("UPDATE marketing_visitors_db SET updated_at = CURRENT_TIMESTAMP - INTERVAL '91 days' WHERE id = ?", visitor);
        assertThat(outbox.claim()).isEmpty();
        assertThat(outbox.browserEvents(browser)).isEmpty();
        context.expire();
        assertThat(report.export(registration.getTournament().getId())).doesNotContain("campaign1");
    }

    private PaymentUpdateDto update(TournamentRegistration registration) {
        var update = new PaymentUpdateDto();
        update.setRegistrationId(registration.getId());
        update.setAmount(new BigDecimal("1000"));
        update.setPaymentStatus(PaymentStatus.PAID);
        update.setCurrency("ARS");
        return update;
    }

    private TournamentRegistration registration(RegistrationStatus status) {
        return registrations.saveAndFlush(TournamentRegistration.builder().tournament(tournament(16))
                .player(player()).status(status).build());
    }

    private Tournament tournament(int capacity) {
        Club club = clubs.save(Club.builder().nombre("Клуб " + UUID.randomUUID()).isActive(true).build());
        return tournaments.saveAndFlush(Tournament.builder().nombre("Событие теста").clubId(club.getId())
                .fechaInicio(LocalDate.now().plusDays(7)).horaInicio(LocalTime.NOON)
                .tipo(TournamentType.AMERICANO).modalidad(Modalidad.INDIVIDUAL).generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.TODOS).cupoMax(capacity).precio(new BigDecimal("1000"))
                .moneda("ARS").estado(TournamentStatus.REGISTRO_ABIERTO).contactoOrganizador("test@example.test").build());
    }

    private PlayerPadel player() {
        return players.saveAndFlush(PlayerPadel.builder().nombre("Игрок").apellido("Тест")
                .email(UUID.randomUUID() + "@example.test").passwordHash("test-hash").activo(true)
                .emailConfirmado(true).preferredLocale("es").nivelJugador(Nivel.C9)
                .telegramUsername("test" + UUID.randomUUID().toString().replace("-", "").substring(0, 10)).build());
    }

    private Payment payment(TournamentRegistration registration, String amount, String currency, String notes) {
        return payments.saveAndFlush(Payment.builder().registration(registration).amount(new BigDecimal(amount))
                .currency(currency).status(PaymentStatus.PAID).notes(notes).build());
    }

    private List<String> events(long registrationId) {
        return jdbc.queryForList("SELECT event_name FROM marketing_events_db WHERE registration_id = ? AND channel = 'BROWSER' ORDER BY occurred_at", String.class, registrationId);
    }

    private List<BigDecimal> purchases(long paymentId) {
        return jdbc.queryForList("SELECT amount FROM marketing_events_db WHERE payment_id = ? ORDER BY cumulative_amount", BigDecimal.class, paymentId);
    }

    private int paidParticipants(long paymentId) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(paid_participants), 0) FROM marketing_events_db WHERE payment_id = ?", Integer.class, paymentId);
    }
}
