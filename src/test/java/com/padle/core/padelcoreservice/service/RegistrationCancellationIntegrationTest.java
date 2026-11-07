package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.marketing.MarketingReportService;
import com.padle.core.padelcoreservice.dto.PartnerRegistrationDto;
import org.springframework.dao.DataIntegrityViolationException;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "app.pair-terms.enabled=true",
        "app.analytics.meta.enabled=false", "app.analytics.meta.access-token=",
        "disposable-email-domains.checkers.daily-updated-domains=false",
        "disposable-email-domains.checkers.reload.enabled=false",
        "spring.mail.username=", "spring.mail.password=",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test"
})
@AutoConfigureMockMvc
@Transactional
class RegistrationCancellationIntegrationTest {
    @Autowired TournamentService service;
    @Autowired MockMvc mvc;
    @Autowired SpringTemplateEngine templates;
    @Autowired PairTermsService pairTerms;
    @Autowired PairSettlementService pairSettlements;
    @Autowired DoubleTournamentRegistrationService doubles;
    @Autowired TournamentRepository tournaments;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired PlayerRepository players;
    @Autowired PaymentRepository payments;
    @Autowired ClubRepository clubs;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;
    @Autowired MarketingReportService report;
    @MockitoBean EmailService email;
    @MockitoBean TournamentNotificationService notifications;

    private final List<Long> tournamentIds = new ArrayList<>();
    private final List<Long> playerIds = new ArrayList<>();
    private final List<Long> clubIds = new ArrayList<>();

    @Test
    void physicalDeleteOfAttributedRegistrationReproducesForeignKeyFailure() {
        TournamentRegistration registration = registration(tournament(1), RegistrationStatus.CONFIRMED);
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            var savepoint = connection.setSavepoint();
            try (var statement = connection.prepareStatement("DELETE FROM tournament_registrations_db WHERE id = ?")) {
                statement.setLong(1, registration.getId());
                assertThatThrownBy(statement::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("23503"));
            } finally {
                connection.rollback(savepoint);
                connection.releaseSavepoint(savepoint);
            }
            return null;
        });
        assertThat(registrations.findById(registration.getId())).isPresent();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cancellationPreservesPlayerOtherEventsPaymentAndSource(boolean attributed) {
        Tournament tournament = tournament(1);
        TournamentRegistration registration = registration(tournament, RegistrationStatus.CONFIRMED);
        TournamentRegistration other = registrations.saveAndFlush(TournamentRegistration.builder()
                .tournament(tournament(1)).player(registration.getPlayer()).status(RegistrationStatus.CONFIRMED).build());
        Payment payment = payments.saveAndFlush(Payment.builder().registration(registration)
                .amount(new BigDecimal("1000")).currency("ARS").status(PaymentStatus.PAID).build());
        if (attributed) {
            jdbc.update("UPDATE marketing_registrations_db SET first_touch = 'campaign-original' WHERE registration_id = ?", registration.getId());
        } else {
            jdbc.update("DELETE FROM marketing_registrations_db WHERE registration_id = ?", registration.getId());
        }

        service.adminRemovePlayerRegistration(tournament.getId(), registration.getPlayer().getId());
        service.adminRemovePlayerRegistration(tournament.getId(), registration.getPlayer().getId());
        entities.flush();
        entities.clear();

        TournamentRegistration cancelled = registrations.findById(registration.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(cancelled.getIsActive()).isFalse();
        assertThat(cancelled.getCancellationDate()).isNotNull();
        assertThat(players.findById(registration.getPlayer().getId())).isPresent();
        assertThat(registrations.findById(other.getId()).orElseThrow().getIsActive()).isTrue();
        assertThat(payments.findById(payment.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(registrations.countByTournamentIdAndStatus(tournament.getId(), RegistrationStatus.CONFIRMED)).isZero();
        if (attributed) {
            assertThat(report.export(tournament.getId())).contains("CANCELLED", "campaign-original", "\"f\"");
        }
        verifyNoInteractions(email);
    }

    @Test
    void repeatedPairCancellationPreservesBothRowsAndPayments() {
        Tournament tournament = tournament(1);
        tournament.setModalidad(Modalidad.DOBLES);
        TournamentRegistration first = registration(tournament, RegistrationStatus.CONFIRMED);
        TournamentRegistration second = registration(tournament, RegistrationStatus.CONFIRMED);
        first.setPartner(second.getPlayer());
        second.setPartner(first.getPlayer());
        for (TournamentRegistration row : List.of(first, second)) {
            row.setIsDoubleRegistration(true);
            row.setMainPlayerId(first.getPlayer().getId());
        }
        registrations.saveAllAndFlush(List.of(first, second));
        Payment payment = payments.saveAndFlush(Payment.builder().registration(first)
                .amount(new BigDecimal("1000")).currency("ARS").status(PaymentStatus.PAID).build());
        service.adminRemovePlayerRegistration(tournament.getId(), second.getPlayer().getId());
        service.cancelPairRegistration(tournament.getId(), first.getPlayer().getId(), "repeat");
        entities.flush();
        assertThat(registrations.findByTournamentId(tournament.getId()))
                .hasSize(2).allSatisfy(row -> {
                    assertThat(row.getIsActive()).isFalse();
                    assertThat(row.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
                });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM marketing_registrations_db WHERE registration_id IN (?, ?)",
                Integer.class, first.getId(), second.getId())).isEqualTo(2);
        assertThat(payments.findById(payment.getId())).isPresent();
        verifyNoInteractions(email);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void leavingConfirmedPairKeepsPartnerPlaceUntilWholeEntryIsCancelled(boolean mainPlayerLeaves) {
        Tournament tournament = tournament(1);
        tournament.setModalidad(Modalidad.DOBLES);
        TournamentRegistration main = registration(tournament, RegistrationStatus.CONFIRMED);
        TournamentRegistration partner = registration(tournament, RegistrationStatus.CONFIRMED);
        TournamentRegistration reserveMain = registration(tournament, RegistrationStatus.WAITLIST);
        TournamentRegistration reservePartner = registration(tournament, RegistrationStatus.WAITLIST);
        pair(main, partner);
        pair(reserveMain, reservePartner);

        TournamentRegistration leaving = mainPlayerLeaves ? main : partner;
        TournamentRegistration staying = mainPlayerLeaves ? partner : main;
        service.cancelRegistration(tournament.getId(), leaving.getPlayer().getId(), "No puedo asistir");
        entities.flush();
        entities.clear();

        TournamentRegistration remaining = registrations.findById(staying.getId()).orElseThrow();
        assertThat(remaining.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(remaining.getIsActive()).isTrue();
        assertThat(remaining.getPartner()).isNull();
        assertThat(remaining.getMainPlayerId()).isEqualTo(staying.getPlayer().getId());
        assertThat(registrations.countConfirmedPairs(tournament.getId())).isEqualTo(1);
        assertThat(registrations.findById(reserveMain.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(registrations.findById(reservePartner.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.WAITLIST);

        service.cancelPairRegistration(tournament.getId(), staying.getPlayer().getId(), "Cancelamos todo");
        entities.flush();
        entities.clear();
        assertThat(registrations.findById(reserveMain.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registrations.findById(reservePartner.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registrations.countConfirmedPairs(tournament.getId())).isEqualTo(1);
    }

    @Test
    void leavingWaitlistPairKeepsOriginalQueuePosition() throws Exception {
        Tournament tournament = tournament(1);
        tournament.setModalidad(Modalidad.DOBLES);
        TournamentRegistration main = registration(tournament, RegistrationStatus.WAITLIST);
        TournamentRegistration partner = registration(tournament, RegistrationStatus.WAITLIST);
        pair(main, partner);

        service.cancelRegistration(tournament.getId(), main.getPlayer().getId(), "No puedo asistir");
        entities.flush();
        entities.clear();

        TournamentRegistration remaining = registrations.findById(partner.getId()).orElseThrow();
        assertThat(remaining.getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(remaining.getWaitlistPosition()).isEqualTo(1);
        assertThat(remaining.getPartner()).isNull();
        assertThat(remaining.getIsDoubleRegistration()).isFalse();
        assertThat(remaining.getMainPlayerId()).isEqualTo(partner.getPlayer().getId());

        mvc.perform(get("/players/tournaments/" + tournament.getId() + "/my-registration")
                        .with(user(remaining.getPlayer())))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"replacementNeeded":true,"status":"WAITLIST","waitlistPosition":1}
                        """));
    }

    @Test
    void replacingPartnerInReserveKeepsOriginalQueuePosition() {
        Tournament tournament = tournament(1);
        tournament.setModalidad(Modalidad.DOBLES);
        pair(registration(tournament, RegistrationStatus.CONFIRMED),
                registration(tournament, RegistrationStatus.CONFIRMED));
        TournamentRegistration waitingMain = registration(tournament, RegistrationStatus.WAITLIST);
        TournamentRegistration waitingPartner = registration(tournament, RegistrationStatus.WAITLIST);
        pair(waitingMain, waitingPartner);
        service.cancelRegistration(tournament.getId(), waitingMain.getPlayer().getId(), "No puedo asistir");
        PlayerPadel replacement = players.saveAndFlush(PlayerPadel.builder()
                .nombre("Nuevo").apellido("Jugador").email(UUID.randomUUID() + "@example.test")
                .passwordHash("test-hash").activo(true).emailConfirmado(true)
                .preferredLocale("es").nivelJugador(Nivel.C9).build());
        playerIds.add(replacement.getId());

        doubles.addPartnerToSoloRegistration(tournament.getId(), waitingPartner.getPlayer().getId(),
                PartnerRegistrationDto.builder().nombre(replacement.getNombre())
                        .apellido(replacement.getApellido()).email(replacement.getEmail()).build());
        entities.flush();
        entities.clear();

        assertThat(registrations.findById(waitingPartner.getId()).orElseThrow().getWaitlistPosition())
                .isEqualTo(1);
        assertThat(registrations.findByTournamentIdAndPlayerId(tournament.getId(), replacement.getId())
                .orElseThrow().getWaitlistPosition()).isEqualTo(1);
        assertThat(registrations.countConfirmedPairs(tournament.getId())).isEqualTo(1);
    }

    @Test
    void cancellingFullPairPromotesReserveWhenOtherConfirmedPairsRemain() {
        Tournament tournament = tournament(3);
        tournament.setModalidad(Modalidad.DOBLES);
        TournamentRegistration leavingMain = registration(tournament, RegistrationStatus.CONFIRMED);
        TournamentRegistration leavingPartner = registration(tournament, RegistrationStatus.CONFIRMED);
        pair(leavingMain, leavingPartner);
        for (int i = 0; i < 2; i++) {
            pair(registration(tournament, RegistrationStatus.CONFIRMED),
                    registration(tournament, RegistrationStatus.CONFIRMED));
        }
        TournamentRegistration reserveMain = registration(tournament, RegistrationStatus.WAITLIST);
        TournamentRegistration reservePartner = registration(tournament, RegistrationStatus.WAITLIST);
        pair(reserveMain, reservePartner);

        service.cancelPairRegistration(tournament.getId(), leavingMain.getPlayer().getId(), "Cancelamos todo");
        entities.flush();
        entities.clear();

        assertThat(registrations.findById(reserveMain.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registrations.findById(reservePartner.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registrations.countConfirmedPairs(tournament.getId())).isEqualTo(3);
    }

    @Test
    void cancellingSoloPlacePromotesOnlyOneReadyPair() {
        Tournament tournament = tournament(2);
        tournament.setModalidad(Modalidad.DOBLES);
        TournamentRegistration leavingSolo = registration(tournament, RegistrationStatus.SOLO_ADD_LATER);
        registration(tournament, RegistrationStatus.SOLO_ADD_LATER);
        TournamentRegistration reserveMain = registration(tournament, RegistrationStatus.WAITLIST);
        TournamentRegistration reservePartner = registration(tournament, RegistrationStatus.WAITLIST);
        pair(reserveMain, reservePartner);

        service.cancelPairRegistration(tournament.getId(), leavingSolo.getPlayer().getId(), "Cancelé mi plaza");
        entities.flush();
        entities.clear();

        assertThat(registrations.findById(reserveMain.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registrations.findById(reservePartner.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registrations.countConfirmedPairs(tournament.getId())).isEqualTo(1);
        assertThat(registrations.countActiveSoloRegistrations(tournament.getId())).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void replacingPlayerKeepsCancelledRegistrationAndPaymentHistory(boolean selectById) throws Exception {
        Tournament tournament = tournament(1);
        tournament.setModalidad(Modalidad.DOBLES);
        TournamentRegistration main = registration(tournament, RegistrationStatus.CONFIRMED);
        TournamentRegistration partner = registration(tournament, RegistrationStatus.CONFIRMED);
        pair(main, partner);
        Payment payment = payments.saveAndFlush(Payment.builder().registration(main)
                .amount(new BigDecimal("1000")).currency("ARS").status(PaymentStatus.PAID).build());
        PlayerPadel replacement = players.saveAndFlush(PlayerPadel.builder()
                .nombre("Nuevo").apellido("Jugador").email(UUID.randomUUID() + "@example.test")
                .telefono("+5491155500088")
                .passwordHash("test-hash").activo(true).emailConfirmado(true)
                .preferredLocale("es").nivelJugador(Nivel.C9).build());
        playerIds.add(replacement.getId());

        String path = "/api/tournaments/double/" + tournament.getId() + "/replace-self";
        String body = selectById ? """
                {"nombre":"Nuevo","apellido":"Jugador","existingUserId":%s}
                """.formatted(replacement.getId()) : """
                {"nombre":"Nuevo","apellido":"Jugador","telefono":"+5491155500088"}
                """;
        mvc.perform(post(path).with(user(main.getPlayer())).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isNoContent());
        entities.flush();
        entities.clear();

        assertThat(registrations.findById(main.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(payments.findById(payment.getId()).orElseThrow().getRegistration().getId())
                .isEqualTo(main.getId());
        assertThat(registrations.findByTournamentIdAndPlayerId(tournament.getId(), partner.getPlayer().getId())
                .orElseThrow().getPartner().getId()).isEqualTo(replacement.getId());
        assertThat(registrations.findByTournamentIdAndPlayerId(tournament.getId(), replacement.getId())
                .orElseThrow().getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registrations.countConfirmedPairs(tournament.getId())).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void bookerCanStayAndReplacePartnerImmediately(boolean confirmed) throws Exception {
        var tournament = tournament(2);
        tournament.setTipo(TournamentType.AMERICANO_TEAMS);
        tournament.setModalidad(Modalidad.DOBLES);
        var registrationStatus = confirmed ? RegistrationStatus.CONFIRMED : RegistrationStatus.WAITLIST;
        var booker = registration(tournament, registrationStatus);
        var oldPartner = registration(tournament, registrationStatus);
        UUID bookingId = UUID.randomUUID();
        for (var row : List.of(booker, oldPartner)) {
            row.setPairBookingId(bookingId);
            row.setPairBookedBy(booker.getPlayer().getId());
        }
        pair(booker, oldPartner);
        var replacement = players.saveAndFlush(PlayerPadel.builder()
                .nombre("Алекс").apellido("Тестов").email(UUID.randomUUID() + "@example.test")
                .passwordHash("test-hash").activo(true).emailConfirmado(true)
                .preferredLocale("ru").nivelJugador(Nivel.C9).build());
        playerIds.add(replacement.getId());

        String registrationPath = "/players/tournaments/" + tournament.getId() + "/my-registration";
        mvc.perform(get(registrationPath).with(user(booker.getPlayer())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canReplacePartner").value(true));
        mvc.perform(get(registrationPath).with(user(oldPartner.getPlayer())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.canReplacePartner").value(false));

        String path = "/api/tournaments/double/" + tournament.getId() + "/replace-partner";
        String body = """
                {"currentPartnerId":%s,"replacement":%s}
                """.formatted(oldPartner.getPlayer().getId(), confirmed ? """
                {"nombre":"Алекс","apellido":"Тестов","existingUserId":%s}
                """.formatted(replacement.getId()) : """
                {"nombre":"Алекс","apellido":"Тестов","telefono":"+5491155500881"}
                """);
        mvc.perform(post(path).with(user(booker.getPlayer())).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isNoContent());
        entities.flush();
        entities.clear();

        var staying = registrations.findById(booker.getId()).orElseThrow();
        var added = registrations.findByTournamentIdAndPlayerId(tournament.getId(), staying.getPartner().getId())
                .orElseThrow();
        assertThat(staying.getStatus()).isEqualTo(registrationStatus);
        assertThat(added.getStatus()).isEqualTo(registrationStatus);
        assertThat(added.getPosition()).isEqualTo(staying.getPosition());
        assertThat(added.getWaitlistPosition()).isEqualTo(staying.getWaitlistPosition());
        assertThat(added.getPairBookingId()).isEqualTo(bookingId);
        assertThat(added.getPairBookedBy()).isEqualTo(booker.getPlayer().getId());
        assertThat(added.getPartner().getId()).isEqualTo(booker.getPlayer().getId());
        assertThat(added.getPlayer().getNombreCompleto()).isEqualTo("Алекс Тестов");
        if (confirmed) assertThat(added.getPlayer().getId()).isEqualTo(replacement.getId());
        assertThat(registrations.findById(oldPartner.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.CANCELLED);

        String notices = "/api/players/me/pair-replacement-notices";
        mvc.perform(get(notices).with(user(oldPartner.getPlayer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].bookerName").value(booker.getPlayer().getNombreCompleto()))
                .andExpect(jsonPath("$[0].replacementName").value("Алекс Тестов"));
        Long noticeId = jdbc.queryForObject("SELECT id FROM pair_replacement_notices_db WHERE player_id = ?",
                Long.class, oldPartner.getPlayer().getId());
        mvc.perform(get(notices).with(user(booker.getPlayer())))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(post(notices + "/" + noticeId + "/dismiss").with(user(booker.getPlayer())).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get(notices).with(user(oldPartner.getPlayer())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(noticeId.intValue()));
        mvc.perform(post(notices + "/" + noticeId + "/dismiss").with(user(oldPartner.getPlayer())).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get(notices).with(user(oldPartner.getPlayer())))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(notifications).sendPartnerReplacedNotice(
                argThat(player -> player.getId().equals(oldPartner.getPlayer().getId())), any());

        // A retry with the previous partner cannot overwrite the new pair.
        mvc.perform(post(path).with(user(booker.getPlayer())).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict());
    }

    private void pair(TournamentRegistration first, TournamentRegistration second) {
        first.setPartner(second.getPlayer());
        second.setPartner(first.getPlayer());
        for (TournamentRegistration row : List.of(first, second)) {
            row.setIsDoubleRegistration(true);
            row.setMainPlayerId(first.getPlayer().getId());
        }
        registrations.saveAllAndFlush(List.of(first, second));
    }

    @Test
    void pairCanRegisterAgainWithoutDeletingItsPaymentOrAttribution() {
        Tournament tournament = tournament(1);
        tournament.setModalidad(Modalidad.DOBLES);
        TournamentRegistration first = registration(tournament, RegistrationStatus.CANCELLED);
        TournamentRegistration second = registration(tournament, RegistrationStatus.CANCELLED);
        first.setIsActive(false);
        second.setIsActive(false);
        registrations.saveAllAndFlush(List.of(first, second));
        Payment payment = payments.saveAndFlush(Payment.builder().registration(first)
                .amount(new BigDecimal("1000")).currency("ARS").status(PaymentStatus.PAID).build());
        jdbc.update("UPDATE marketing_registrations_db SET first_touch = 'original-source' WHERE registration_id = ?", first.getId());

        doubles.registerForDoubleTournament(service.getTournamentDtoById(tournament.getId()).orElseThrow(),
                first.getPlayer().getId(), PartnerRegistrationDto.builder()
                        .existingUserId(second.getPlayer().getId()).isExistingUser(true).build());
        entities.flush();
        entities.clear();

        assertThat(registrations.findByTournamentId(tournament.getId())).hasSize(2)
                .allSatisfy(row -> {
                    assertThat(row.getIsActive()).isTrue();
                    assertThat(row.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
                });
        assertThat(payments.findById(payment.getId()).orElseThrow().getRegistration().getId()).isEqualTo(first.getId());
        assertThat(report.export(tournament.getId())).contains("original-source");
    }

    @Test
    void bookerAcceptsForBothAndCancellationUsesTheSamePaymentQuote() {
        Tournament tournament = tournament(1);
        tournament.setTipo(TournamentType.AMERICANO_TEAMS);
        tournament.setModalidad(Modalidad.DOBLES);
        var start = PairTermsService.now().plusHours(2);
        tournament.setFechaInicio(start.toLocalDate());
        tournament.setHoraInicio(start.toLocalTime().withNano(0));
        var booker = registration(tournament, RegistrationStatus.CONFIRMED);
        var partner = registration(tournament, RegistrationStatus.CONFIRMED);
        pair(booker, partner);
        Long bookerId = booker.getPlayer().getId();
        Long partnerId = partner.getPlayer().getId();
        assertThat(pairTerms.bookingTerms(tournament.getId(), bookerId).canAccept()).isFalse();
        pairSettlements.verifyLegacyBooker(tournament.getId(), bookerId, 42L);
        UUID bookingId = booker.getPairBookingId();
        assertThat(bookingId).isNotNull().isEqualTo(partner.getPairBookingId());
        assertThat(pairTerms.cancellation(tournament.getId(), bookerId).legacy()).isTrue();
        assertThat(pairTerms.bookingTerms(tournament.getId(), partnerId).canAccept()).isFalse();
        var offer = pairTerms.bookingTerms(tournament.getId(), bookerId);
        assertThat(offer.canAccept()).isTrue();
        pairTerms.accept(tournament.getId(), bookerId, offer.terms().token());
        assertThat(pairTerms.bookingTerms(tournament.getId(), partnerId).accepted()).isTrue();
        var beforePayment = pairTerms.cancellation(tournament.getId(), partnerId);
        payments.saveAndFlush(Payment.builder().registration(booker).pairBookingId(bookingId)
                .amount(new BigDecimal("400")).currency("ARS").status(PaymentStatus.PAID).build());
        assertThatThrownBy(() -> service.confirmPairCancellation(tournament.getId(), partnerId, beforePayment.token()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("PAIR_TERMS_CHANGED");
        assertThat(booker.getIsActive()).isTrue();
        assertThat(partner.getIsActive()).isTrue();
    }

    @Test
    void wholePairCancellationAfterBookerLeavesCreditsTheirPayment() throws Exception {
        Tournament tournament = tournament(1);
        tournament.setTipo(TournamentType.AMERICANO_TEAMS);
        tournament.setModalidad(Modalidad.DOBLES);
        var start = PairTermsService.now().plusHours(2);
        tournament.setFechaInicio(start.toLocalDate());
        tournament.setHoraInicio(start.toLocalTime().withNano(0));
        var booker = registration(tournament, RegistrationStatus.CONFIRMED);
        var partner = registration(tournament, RegistrationStatus.CONFIRMED);
        pair(booker, partner);
        UUID bookingId = UUID.randomUUID();
        Long bookerId = booker.getPlayer().getId();
        Long partnerId = partner.getPlayer().getId();
        var offer = pairTerms.offer(tournament.getId()).orElseThrow();
        for (var row : List.of(booker, partner)) {
            row.setPairBookingId(bookingId);
            row.setPairBookedBy(bookerId);
            PairTermsService.recordAcceptance(row, offer, bookerId, PairTermsService.now());
        }
        payments.saveAndFlush(Payment.builder().registration(booker).pairBookingId(bookingId)
                .amount(new BigDecimal("400")).currency("ARS").status(PaymentStatus.PAID).build());

        service.cancelRegistration(tournament.getId(), bookerId, null);
        assertThat(partner.getIsActive()).isTrue();
        var quote = pairTerms.cancellation(tournament.getId(), partnerId);
        assertThat(quote.late()).isTrue();
        assertThat(quote.remainingDue()).isEqualByComparingTo("600");
        String path = "/api/tournaments/double/" + tournament.getId() + "/pair-cancellation";
        mvc.perform(get(path).with(user(partner.getPlayer())))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"remainingDue":600,"token":"%s","freeUntil":"%s"}
                        """.formatted(quote.token(), quote.freeUntil()
                                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME))));
        mvc.perform(post(path).with(user(partner.getPlayer())).with(csrf())
                        .contentType("application/json").content("""
                                {"token":"%s"}
                                """.formatted(quote.token())))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"remainingDue":600}
                        """));
        assertThat(partner.getIsActive()).isFalse();
        var mail = new Context(Locale.forLanguageTag("ru"));
        mail.setVariable("pairTerms", offer);
        mail.setVariable("pairWaitlist", false);
        mail.setVariable("baseUrl", "https://example.test");
        mail.setVariable("tournamentName", offer.eventName());
        mail.setVariable("playerName", partner.getPlayer().getNombre());
        assertThat(templates.process("email/pair-terms", mail)).contains("Бесплатная отмена до", "ARS");
        assertThat(jdbc.queryForObject("SELECT remaining_due FROM pair_cancellations_db WHERE booking_id = ?",
                BigDecimal.class, bookingId)).isEqualByComparingTo("600");

        var replacement = registration(tournament, RegistrationStatus.CONFIRMED);
        var replacementPartner = registration(tournament, RegistrationStatus.CONFIRMED);
        pair(replacement, replacementPartner);
        UUID replacementId = UUID.randomUUID();
        replacement.setPairBookingId(replacementId);
        replacementPartner.setPairBookingId(replacementId);
        entities.flush();
        assertThatThrownBy(() -> pairSettlements.recordPaidReplacement(tournament.getId(), bookingId, replacementId, 42L))
                .isInstanceOf(IllegalStateException.class).hasMessage("PAIR_REPLACEMENT_NOT_PAID");
    }

    @Test
    void paidReplacementWaivesObligationAndCreditsTheOriginalPayments() {
        Tournament tournament = tournament(1);
        tournament.setTipo(TournamentType.AMERICANO_TEAMS);
        tournament.setModalidad(Modalidad.DOBLES);
        var first = registration(tournament, RegistrationStatus.CONFIRMED);
        var second = registration(tournament, RegistrationStatus.CONFIRMED);
        pair(first, second);
        UUID original = UUID.randomUUID();
        Long playerId = first.getPlayer().getId();
        var offer = pairTerms.offer(tournament.getId()).orElseThrow();
        for (var row : List.of(first, second)) {
            row.setPairBookingId(original);
            row.setPairBookedBy(playerId);
            PairTermsService.recordAcceptance(row, offer, playerId, PairTermsService.now());
        }
        payments.saveAndFlush(Payment.builder().registration(first).pairBookingId(original)
                .amount(new BigDecimal("400")).currency("ARS").status(PaymentStatus.PAID).build());
        var quote = pairTerms.cancellation(tournament.getId(), playerId);
        service.confirmPairCancellation(tournament.getId(), playerId, quote.token());

        var replacement = registration(tournament, RegistrationStatus.CONFIRMED);
        var replacementPartner = registration(tournament, RegistrationStatus.CONFIRMED);
        pair(replacement, replacementPartner);
        UUID replacementId = UUID.randomUUID();
        replacement.setPairBookingId(replacementId);
        replacementPartner.setPairBookingId(replacementId);
        payments.saveAndFlush(Payment.builder().registration(replacement).pairBookingId(replacementId)
                .amount(new BigDecimal("2000")).currency("ARS").status(PaymentStatus.PAID).build());

        pairSettlements.recordPaidReplacement(tournament.getId(), original, replacementId, 42L);
        var settlement = pairSettlements.settlements(tournament.getId()).get(0);
        assertThat(settlement.obligation()).isZero();
        assertThat(settlement.due()).isZero();
        assertThat(settlement.refund()).isEqualByComparingTo("400");
        assertThat(settlement.replacementBookingId()).isEqualTo(replacementId);

    }

    @Test
    void organizerRecordsNoShowOnceAndCreditsPaidAmount() {
        Tournament tournament = tournament(1);
        tournament.setTipo(TournamentType.AMERICANO_TEAMS);
        tournament.setModalidad(Modalidad.DOBLES);
        var booker = registration(tournament, RegistrationStatus.CONFIRMED);
        var partner = registration(tournament, RegistrationStatus.CONFIRMED);
        pair(booker, partner);
        UUID bookingId = UUID.randomUUID();
        Long bookerId = booker.getPlayer().getId();
        var offer = pairTerms.offer(tournament.getId()).orElseThrow();
        for (var row : List.of(booker, partner)) {
            row.setPairBookingId(bookingId);
            row.setPairBookedBy(bookerId);
            PairTermsService.recordAcceptance(row, offer, bookerId, PairTermsService.now());
        }
        var start = PairTermsService.now().minusHours(1);
        tournament.setFechaInicio(start.toLocalDate());
        tournament.setHoraInicio(start.toLocalTime().withNano(0));
        payments.saveAndFlush(Payment.builder().registration(booker).pairBookingId(bookingId)
                .amount(new BigDecimal("400")).currency("ARS").status(PaymentStatus.PAID).build());

        pairSettlements.recordNoShow(tournament.getId(), bookingId, 42L);
        pairSettlements.recordNoShow(tournament.getId(), bookingId, 42L);
        assertThat(pairSettlements.settlements(tournament.getId())).singleElement().satisfies(row -> {
            assertThat(row.noShow()).isTrue();
            assertThat(row.due()).isEqualByComparingTo("600");
        });
    }

    @Test
    void databaseFailureRollsBackCancellationAndDoesNotSendEmail() {
        Tournament tournament = tournament(1);
        TournamentRegistration registration = registration(tournament, RegistrationStatus.CONFIRMED);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        jdbc.execute("ALTER TABLE tournament_registrations_db ADD CONSTRAINT test_cancel_failure CHECK (id <> "
                + registration.getId() + " OR status <> 'CANCELLED')");
        assertThatThrownBy(() -> service.adminRemovePlayerRegistration(tournament.getId(), registration.getPlayer().getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        TestTransaction.flagForRollback();
        TestTransaction.end();
        TestTransaction.start();
        assertThat(registrations.findById(registration.getId()).orElseThrow().getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        verifyNoInteractions(email);
    }

    @Test
    void rollbackRestoresCompositionReserveAndSuppressesAllEmails() {
        Tournament tournament = tournament(1);
        TournamentRegistration confirmed = registration(tournament, RegistrationStatus.CONFIRMED);
        TournamentRegistration reserve = registration(tournament, RegistrationStatus.WAITLIST);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        service.adminRemovePlayerRegistration(tournament.getId(), confirmed.getPlayer().getId());
        entities.flush();
        assertThat(registrations.findById(reserve.getId()).orElseThrow().getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        verifyNoInteractions(email);
        TestTransaction.flagForRollback();
        TestTransaction.end();
        TestTransaction.start();

        assertThat(registrations.findById(confirmed.getId()).orElseThrow().getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registrations.findById(reserve.getId()).orElseThrow().getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
        verifyNoInteractions(email);
    }

    @Test
    void concurrentCancellationPromotesReserveAndNotifiesOnlyOnceAfterCommit() throws Exception {
        Tournament tournament = tournament(1);
        TournamentRegistration confirmed = registration(tournament, RegistrationStatus.CONFIRMED);
        TournamentRegistration reserve = registration(tournament, RegistrationStatus.WAITLIST);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> cancelAfter(start, tournament.getId(), confirmed.getPlayer().getId()));
            var second = executor.submit(() -> cancelAfter(start, tournament.getId(), confirmed.getPlayer().getId()));
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        TestTransaction.start();
        assertThat(registrations.findById(confirmed.getId()).orElseThrow().getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(registrations.findById(reserve.getId()).orElseThrow().getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registrations.countByTournamentIdAndStatus(tournament.getId(), RegistrationStatus.CONFIRMED)).isEqualTo(1);
        verify(email, times(1)).sendRegistrationCancelledEmail(eq(confirmed.getPlayer().getEmail()), any(), any(), any(), any(), any(), any(), any());
        verify(email, times(1)).sendWaitlistAutoConfirmEmail(eq(reserve.getPlayer().getEmail()), any(), any(), any(), any(), any(), any(), any());
    }

    private void cancelAfter(CountDownLatch start, Long tournamentId, Long playerId) {
        try {
            assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
            service.adminRemovePlayerRegistration(tournamentId, playerId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private Tournament tournament(int capacity) {
        Club club = clubs.saveAndFlush(Club.builder().nombre("Клуб отмены " + UUID.randomUUID()).isActive(true).build());
        clubIds.add(club.getId());
        Tournament tournament = tournaments.saveAndFlush(Tournament.builder().nombre("Тест отмены")
                .clubId(club.getId()).fechaInicio(LocalDate.now().plusDays(7)).horaInicio(LocalTime.NOON)
                .tipo(TournamentType.CANCHA_ABIERTA).modalidad(Modalidad.INDIVIDUAL).generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.TODOS).cupoMax(capacity).precio(new BigDecimal("1000"))
                .moneda("ARS").estado(TournamentStatus.REGISTRO_ABIERTO).contactoOrganizador("test@example.test").build());
        tournamentIds.add(tournament.getId());
        return tournament;
    }

    private TournamentRegistration registration(Tournament tournament, RegistrationStatus status) {
        PlayerPadel player = players.saveAndFlush(PlayerPadel.builder().nombre("Игрок").apellido("Отмена")
                .email(UUID.randomUUID() + "@example.test").passwordHash("test-hash").activo(true)
                .emailConfirmado(true).preferredLocale("es").nivelJugador(Nivel.C9).build());
        playerIds.add(player.getId());
        return registrations.saveAndFlush(TournamentRegistration.builder().tournament(tournament).player(player)
                .status(status).position(status == RegistrationStatus.CONFIRMED ? 1 : null)
                .waitlistPosition(status == RegistrationStatus.WAITLIST ? 1 : null).build());
    }

    @AfterTransaction
    void removeOnlyCommittedFixtures() {
        for (Long id : tournamentIds) {
            jdbc.update("DELETE FROM marketing_events_db WHERE tournament_id = ?", id);
            jdbc.update("DELETE FROM marketing_paid_slots_db WHERE registration_id IN (SELECT id FROM tournament_registrations_db WHERE tournament_id = ?)", id);
            jdbc.update("DELETE FROM marketing_payment_progress_db WHERE payment_id IN (SELECT p.id FROM payments_db p JOIN tournament_registrations_db r ON p.registration_id = r.id WHERE r.tournament_id = ?)", id);
            jdbc.update("DELETE FROM payments_db WHERE registration_id IN (SELECT id FROM tournament_registrations_db WHERE tournament_id = ?)", id);
            jdbc.update("DELETE FROM marketing_registrations_db WHERE registration_id IN (SELECT id FROM tournament_registrations_db WHERE tournament_id = ?)", id);
            jdbc.update("DELETE FROM tournament_registrations_db WHERE tournament_id = ?", id);
            jdbc.update("DELETE FROM tournaments_db WHERE id = ?", id);
        }
        playerIds.forEach(id -> jdbc.update("DELETE FROM player_padel_db WHERE id = ?", id));
        clubIds.forEach(id -> jdbc.update("DELETE FROM club_db WHERE id = ?", id));
    }
}
