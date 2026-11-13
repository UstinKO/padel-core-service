package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.marketing.MarketingReportService;
import com.padle.core.padelcoreservice.dto.PartnerRegistrationDto;
import com.padle.core.padelcoreservice.dto.ParticipationDtos.Decision;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.dto.SoloDoubleRegistrationDto;
import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import com.padle.core.padelcoreservice.service.cancellation.RegistrationCancellationService;
import com.padle.core.padelcoreservice.dto.CancellationHistory;
import org.springframework.security.access.AccessDeniedException;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
        "app.participation.response-email.enabled=true",
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
    @Autowired RegistrationCancellationService cancellationHistory;
    @Autowired MockMvc mvc;
    @Autowired SpringTemplateEngine templates;
    @Autowired PairTermsService pairTerms;
    @Autowired TournamentParticipationService participation;
    @Autowired ParticipationRequestRepository participationRequests;
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
    @MockitoSpyBean TournamentNotificationService notifications;

    private final List<Long> tournamentIds = new ArrayList<>();
    private final List<Long> playerIds = new ArrayList<>();
    private final List<Long> clubIds = new ArrayList<>();

    @Test
    void cabinetShowsOwnedUpcomingAndCancellationHistoryWithoutPrivateContactDetails() throws Exception {
        var event = tournament(4);
        var row = registration(event, RegistrationStatus.CONFIRMED);
        var other = registration(event, RegistrationStatus.WAITLIST);
        payments.saveAndFlush(Payment.builder().registration(row).amount(BigDecimal.TEN)
                .status(PaymentStatus.PAID).build());
        payments.saveAndFlush(Payment.builder().registration(row).amount(BigDecimal.TEN)
                .status(PaymentStatus.PENDING).notes("PARTNER_PAYMENT").build());

        String base = "/api/players/me/events";
        mvc.perform(get(base).with(user(row.getPlayer())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.upcoming[0].registrationId").value(row.getId()))
                .andExpect(jsonPath("$.upcoming[0].paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.upcoming[0].availableActions[0]").value("WITHDRAW"))
                .andExpect(jsonPath("$.history.length()").value(0L))
                .andExpect(content().string(not(containsString(other.getPlayer().getEmail()))))
                .andExpect(content().string(not(containsString(row.getPlayer().getEmail()))));

        String withdraw = base + "/" + row.getId() + "/withdraw";
        String version = "{\"version\":0}";
        mvc.perform(post(withdraw).with(user(other.getPlayer())).with(csrf())
                        .contentType("application/json").content(version))
                .andExpect(status().isNotFound());
        mvc.perform(post(withdraw).with(user(row.getPlayer())).contentType("application/json").content(version))
                .andExpect(status().isForbidden());
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(withdraw).with(user(row.getPlayer())).with(csrf())
                            .contentType("application/json").content(version))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(get(base).with(user(row.getPlayer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming.length()").value(0L))
                .andExpect(jsonPath("$.history[0].cancellations.length()").value(1L))
                .andExpect(jsonPath("$.history[0].cancellations[0].initiator").value("PLAYER"));

        row.setIsActive(true);
        row.setStatus(RegistrationStatus.CONFIRMED);
        registrations.saveAndFlush(row);
        mvc.perform(get(base).with(user(row.getPlayer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming[0].version").value(1))
                .andExpect(jsonPath("$.history[0].cancellations.length()").value(1L));
        mvc.perform(post(withdraw).with(user(row.getPlayer())).with(csrf())
                        .contentType("application/json").content(version))
                .andExpect(status().isConflict());
        var clubAdmin = Owner.builder().id(9001L).role(OwnerRole.CLUB_ADMIN).build();
        mvc.perform(get(base).with(user(clubAdmin))).andExpect(status().isForbidden());
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
    }

    @Test
    void cabinetChangesContactVisibilityOnlyForTheOwnedSearchRegistration() throws Exception {
        var event = tournament(4);
        event.setModalidad(Modalidad.DOBLES);
        event.setTipo(TournamentType.AMERICANO_TEAMS);
        var row = registration(event, RegistrationStatus.SOLO_SEARCH);
        row.getPlayer().setTelefono("+5491155551234");
        players.saveAndFlush(row.getPlayer());

        String path = "/api/players/me/events/" + row.getId() + "/contact-visibility";
        String show = "{\"version\":0,\"shareContacts\":true}";
        mvc.perform(put(path).with(user(player())).with(csrf()).contentType("application/json").content(show))
                .andExpect(status().isNotFound());
        mvc.perform(put(path).with(user(row.getPlayer())).with(csrf()).contentType("application/json").content(show))
                .andExpect(status().isNoContent());
        assertThat(doubles.getLookingForPartnerPlayers(event.getId()).get(0).getTelefono())
                .isEqualTo(row.getPlayer().getTelefono());

        mvc.perform(put(path).with(user(row.getPlayer())).with(csrf())
                        .contentType("application/json").content("{\"version\":0,\"shareContacts\":false}"))
                .andExpect(status().isNoContent());
        assertThat(doubles.getLookingForPartnerPlayers(event.getId()).get(0).getTelefono()).isNull();
    }

    @Test
    void playerHttpVersionIsOptionalButStaleHeaderCannotCancelNewCycleOrSpoofActor() throws Exception {
        var event=tournament(2);var row=registration(event,RegistrationStatus.CONFIRMED);
        String path="/players/tournaments/"+event.getId();
        mvc.perform(get(path+"/my-registration").with(user(row.getPlayer())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancellationVersion").value(0));
        mvc.perform(post(path+"/cancel").with(user(row.getPlayer())).with(csrf())
                        .header("If-Registration-Version","0").param("actorKind","OWNER").param("reasonCode","ORGANIZER_REMOVAL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertThat(cancellationHistory.forPlayer(row.getPlayer(),List.of(event.getId())).get(0).initiator())
                .isEqualTo(CancellationHistory.ActorKind.PLAYER);
        row.setIsActive(true);row.setStatus(RegistrationStatus.CONFIRMED);registrations.saveAndFlush(row);
        mvc.perform(post(path+"/cancel").with(user(row.getPlayer())).with(csrf()).header("If-Registration-Version","0").locale(Locale.forLanguageTag("ru")))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("Заявка изменилась")));
        assertThat(row.getIsActive()).isTrue();
    }

    @Test
    void organizerCommentIsScopedValidatedAndDoesNotLeakToPublicEvent() throws Exception {
        var event=tournament(2);var row=registration(event,RegistrationStatus.CONFIRMED);
        var owner=Owner.builder().id(9001L).role(OwnerRole.SUPER_ADMIN).build();
        String path="/test/tournaments/"+event.getId()+"/remove-player/"+row.getPlayer().getId();
        mvc.perform(post(path).with(user(owner)).with(csrf()).param("comment","<script>private</script>"))
                .andExpect(status().isBadRequest());
        assertThat(row.getIsActive()).isTrue();
        mvc.perform(post(path).with(user(owner)).with(csrf()).param("comment","Причина для участника"))
                .andExpect(status().isOk());
        assertThat(cancellationHistory.forPlayer(row.getPlayer(),List.of(event.getId())).get(0).organizerComment())
                .isEqualTo("Причина для участника");
        assertThat(cancellationHistory.forPlayer(player(),List.of(event.getId()))).isEmpty();
        mvc.perform(get("/api/public/events/"+event.getId())).andExpect(content().string(not(containsString("Причина для участника"))));
    }

    @Test
    void immutableHistorySurvivesReactivationAndRejectsStaleVersion() {
        var event=tournament(2);var row=registration(event,RegistrationStatus.CONFIRMED);
        var id=row.getId();var player=row.getPlayer();
        service.cancelRegistration(event.getId(),player.getId(),"private text",0L);
        service.cancelRegistration(event.getId(),player.getId(),"retry",0L);
        var first=cancellationHistory.forPlayer(player,List.of(event.getId()));
        assertThat(first).hasSize(1);
        assertThat(first.get(0).initiator()).isEqualTo(CancellationHistory.ActorKind.PLAYER);
        assertThat(first.get(0).reasonCode()).isEqualTo(CancellationHistory.Reason.PARTICIPANT_WITHDRAWAL);
        assertThat(first.get(0).organizerComment()).isNull();
        row.setIsActive(true);row.setStatus(RegistrationStatus.CONFIRMED);row.setCancellationDate(null);row.setCancellationReason(null);
        row.setRegistrationDate(row.getRegistrationDate().plusSeconds(1));registrations.saveAndFlush(row);
        assertThatThrownBy(()->service.cancelRegistration(event.getId(),player.getId(),null,0L)).isInstanceOf(TournamentRegistrationException.class);
        assertThat(row.getIsActive()).isTrue();
        service.cancelRegistration(event.getId(),player.getId(),null,1L);entities.flush();entities.clear();
        assertThat(registrations.findById(id).orElseThrow().getCancellationVersion()).isEqualTo(2L);
        var history=cancellationHistory.forPlayer(player,List.of(event.getId()));
        assertThat(history).hasSize(2);
        assertThat(history).extracting(CancellationHistory.Entry::version).containsExactlyInAnyOrder(1L,2L);
        assertThat(history).extracting(CancellationHistory.Entry::eventId).contains(first.get(0).eventId());
    }

    @Test
    void historyIsScopedToParticipantAndManagedClub() {
        var event=tournament(2);var row=registration(event,RegistrationStatus.CONFIRMED);
        var manager=Owner.builder().id(9000L).role(OwnerRole.CLUB_ADMIN).clubId(event.getClubId()).build();
        var stranger=Owner.builder().id(9001L).role(OwnerRole.CLUB_ADMIN).clubId(event.getClubId()+10000).build();
        service.adminRemovePlayerRegistration(event.getId(),row.getPlayer().getId(),manager);
        assertThat(cancellationHistory.forPlayer(player(),List.of(event.getId()))).isEmpty();
        assertThat(cancellationHistory.forPlayer(row.getPlayer(),List.of(event.getId()))).singleElement()
                .satisfies(entry->assertThat(entry.initiator()).isEqualTo(CancellationHistory.ActorKind.OWNER));
        assertThat(cancellationHistory.forManager(manager,event.getId())).hasSize(1);
        assertThatThrownBy(()->cancellationHistory.forManager(stranger,event.getId())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.adminRemovePlayerRegistration(event.getId(),row.getPlayer().getId(),stranger)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->cancellationHistory.forPlayer(null,List.of(event.getId()))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void fullPairCreatesOneOperationAndSafeReplayOfPairTerms() {
        var event=tournament(2);event.setModalidad(Modalidad.DOBLES);
        var first=registration(event,RegistrationStatus.CONFIRMED);var second=registration(event,RegistrationStatus.CONFIRMED);pair(first,second);
        var token=pairTerms.cancellation(event.getId(),first.getPlayer().getId()).token();
        var result=service.confirmPairCancellation(event.getId(),first.getPlayer().getId(),token);
        assertThat(service.confirmPairCancellation(event.getId(),first.getPlayer().getId(),token)).isEqualTo(result);
        var left=cancellationHistory.forPlayer(first.getPlayer(),List.of(event.getId()));
        var right=cancellationHistory.forPlayer(second.getPlayer(),List.of(event.getId()));
        assertThat(left).hasSize(1);assertThat(right).hasSize(1);
        assertThat(left.get(0).eventId()).isEqualTo(right.get(0).eventId());
        assertThat(left.get(0).scope()).isEqualTo(CancellationHistory.Scope.PAIR);
        assertThatThrownBy(()->service.confirmPairCancellation(event.getId(),second.getPlayer().getId(),token)).isInstanceOf(TournamentRegistrationException.class);
        first.setIsActive(true);first.setStatus(RegistrationStatus.CONFIRMED);first.setPairBookingId(UUID.randomUUID());
        registrations.saveAndFlush(first);
        assertThatThrownBy(()->service.confirmPairCancellation(event.getId(),first.getPlayer().getId(),token)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void reserveLinkDoesNotInventAuthenticatedPlayerAndCannotCancelReactivatedRow() {
        var event=tournament(2);event.setModalidad(Modalidad.DOBLES);
        var row=registration(event,RegistrationStatus.WAITLIST);row.setPartnerActionToken("test-"+UUID.randomUUID());
        registrations.saveAndFlush(row);String token=row.getPartnerActionToken();
        doubles.cancelReserveByToken(token);doubles.cancelReserveByToken(token);
        var entry=cancellationHistory.forPlayer(row.getPlayer(),List.of(event.getId())).get(0);
        assertThat(entry.initiator()).isEqualTo(CancellationHistory.ActorKind.UNKNOWN);
        row.invalidateActionTokens();row.setIsActive(true);row.setStatus(RegistrationStatus.WAITLIST);registrations.saveAndFlush(row);
        assertThatThrownBy(()->doubles.cancelReserveByToken(token)).isInstanceOf(TournamentRegistrationException.class);
    }

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
    void leavingWaitlistPairKeepsOriginalQueueTime() throws Exception {
        Tournament tournament = tournament(1);
        tournament.setModalidad(Modalidad.DOBLES);
        TournamentRegistration main = registration(tournament, RegistrationStatus.WAITLIST);
        TournamentRegistration partner = registration(tournament, RegistrationStatus.WAITLIST);
        var queuedAt = LocalDateTime.now().minusDays(1).withNano(0);
        main.setQueuedAt(queuedAt);
        partner.setQueuedAt(queuedAt);
        pair(main, partner);

        service.cancelRegistration(tournament.getId(), main.getPlayer().getId(), "No puedo asistir");
        entities.flush();
        entities.clear();

        TournamentRegistration remaining = registrations.findById(partner.getId()).orElseThrow();
        assertThat(remaining.getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(remaining.getWaitlistPosition()).isNull();
        assertThat(remaining.getQueuedAt()).isEqualTo(queuedAt);
        assertThat(remaining.getPartner()).isNull();
        assertThat(remaining.getIsDoubleRegistration()).isFalse();
        assertThat(remaining.getMainPlayerId()).isEqualTo(partner.getPlayer().getId());

        mvc.perform(get("/players/tournaments/" + tournament.getId() + "/my-registration")
                        .with(user(remaining.getPlayer())))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"replacementNeeded":true,"status":"WAITLIST","waitlistPosition":null}
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
    void cancellingIncompleteReserveDoesNotReleaseAPlace() {
        Tournament tournament = tournament(1);
        tournament.setModalidad(Modalidad.DOBLES);
        var leavingSolo = registration(tournament, RegistrationStatus.SOLO_ADD_LATER);
        pair(registration(tournament, RegistrationStatus.CONFIRMED),
                registration(tournament, RegistrationStatus.CONFIRMED));
        var reserveMain = registration(tournament, RegistrationStatus.WAITLIST);
        var reservePartner = registration(tournament, RegistrationStatus.WAITLIST);
        pair(reserveMain, reservePartner);

        service.cancelPairRegistration(tournament.getId(), leavingSolo.getPlayer().getId(), "Cancelé mi solicitud");
        entities.flush();
        entities.clear();

        assertThat(registrations.findById(reserveMain.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(registrations.findById(reservePartner.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(registrations.countConfirmedPairs(tournament.getId())).isEqualTo(1);
        assertThat(registrations.countActiveSoloRegistrations(tournament.getId())).isZero();
    }

    @Test
    void fullTournamentAcceptsIncompleteEntriesAndKeepsSearchVisible() {
        var tournament = tournament(1);
        tournament.setModalidad(Modalidad.DOBLES);
        pair(registration(tournament, RegistrationStatus.CONFIRMED),
                registration(tournament, RegistrationStatus.CONFIRMED));

        var search = new SoloDoubleRegistrationDto();
        search.setSoloType("SEARCH");
        var addLater = new SoloDoubleRegistrationDto();
        addLater.setSoloType("ADD_LATER");

        var searcher = player();
        var waiting = player();
        assertThat(doubles.registerSoloForDoubleTournament(tournament.getId(), searcher.getId(), search).getStatus())
                .isEqualTo(RegistrationStatus.SOLO_SEARCH);
        assertThat(doubles.registerSoloForDoubleTournament(tournament.getId(), waiting.getId(), addLater).getStatus())
                .isEqualTo(RegistrationStatus.SOLO_ADD_LATER);
        assertThat(doubles.getLookingForPartnerPlayers(tournament.getId())).hasSize(1);
        assertThat(registrations.countConfirmedPairs(tournament.getId())).isEqualTo(1);
        assertThatThrownBy(() -> service.moveToMain(tournament.getId(), waiting.getId()))
                .isInstanceOf(com.padle.core.padelcoreservice.exception.InvalidStateException.class);
    }

    @Test
    void agreedPartnerReusesBothRowsAndInheritsEarlierQueueTime() {
        var tournament = tournament(1);
        tournament.setModalidad(Modalidad.DOBLES);
        var confirmed = registration(tournament, RegistrationStatus.CONFIRMED);
        pair(confirmed, registration(tournament, RegistrationStatus.CONFIRMED));
        var first = registration(tournament, RegistrationStatus.SOLO_SEARCH);
        var second = registration(tournament, RegistrationStatus.SOLO_ADD_LATER);
        var earlier = LocalDateTime.now().minusDays(2).withNano(0);
        var later = LocalDateTime.now().minusDays(1).withNano(0);
        second.setQueuedAt(earlier);
        first.setQueuedAt(later);
        var ready = registration(tournament, RegistrationStatus.WAITLIST);
        var readyPartner = registration(tournament, RegistrationStatus.WAITLIST);
        ready.setQueuedAt(later);
        readyPartner.setQueuedAt(later);
        pair(ready, readyPartner);
        var input = new PartnerRegistrationDto();
        input.setExistingUserId(second.getPlayer().getId());

        doubles.addPartnerToSoloRegistration(tournament.getId(), first.getPlayer().getId(), input);
        assertThat(first.getQueuedAt()).isEqualTo(earlier);
        assertThat(second.getQueuedAt()).isEqualTo(earlier);
        assertThat(first.getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(first.getWaitlistPosition()).isEqualTo(1).isEqualTo(second.getWaitlistPosition());
        assertThat(second.getPartner().getId()).isEqualTo(first.getPlayer().getId());

        service.cancelPairRegistration(tournament.getId(), confirmed.getPlayer().getId(), "Cancelamos");
        assertThat(first.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(second.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(ready.getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(registrations.countConfirmedPairs(tournament.getId())).isEqualTo(1);
    }

    @Test
    void acceptingPairProposalInFullTournamentLeavesBothInReserve() {
        var tournament = tournament(1);
        tournament.setModalidad(Modalidad.DOBLES);
        pair(registration(tournament, RegistrationStatus.CONFIRMED),
                registration(tournament, RegistrationStatus.CONFIRMED));
        var proposer = registration(tournament, RegistrationStatus.SOLO_SEARCH);
        var target = registration(tournament, RegistrationStatus.SOLO_SEARCH);
        String token = "PP_" + UUID.randomUUID();
        target.setPartnerRegistrationToken(token);
        target.setPartnerTokenExpiry(LocalDateTime.now().plusHours(1));
        target.setPairProposerRegId(proposer.getId());
        registrations.flush();

        assertThat(doubles.acceptPairProposal(token).getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(proposer.getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(proposer.getWaitlistPosition()).isEqualTo(target.getWaitlistPosition());
        assertThat(target.getPartnerRegistrationToken()).isNull();
        assertThat(registrations.countConfirmedPairs(tournament.getId())).isEqualTo(1);
        verify(notifications).sendPairRegistrationEmails(proposer, target);
    }

    @Test
    void manualReturnMovesBothRowsAfterWaitingPairsAndFillsVacancy() {
        var tournament = tournament(1);
        tournament.setModalidad(Modalidad.DOBLES);
        var original = registration(tournament, RegistrationStatus.CONFIRMED);
        var originalPartner = registration(tournament, RegistrationStatus.CONFIRMED);
        pair(original, originalPartner);
        var first = registration(tournament, RegistrationStatus.WAITLIST);
        var second = registration(tournament, RegistrationStatus.WAITLIST);
        pair(first, registration(tournament, RegistrationStatus.WAITLIST));
        pair(second, registration(tournament, RegistrationStatus.WAITLIST));

        service.moveToWaitlist(tournament.getId(), originalPartner.getPlayer().getId());

        assertThat(original.getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(originalPartner.getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(original.getQueuedAt()).isEqualTo(originalPartner.getQueuedAt()).isAfter(second.queueTime());
        assertThat(first.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(second.getWaitlistPosition()).isEqualTo(1);
        assertThat(original.getWaitlistPosition()).isEqualTo(2).isEqualTo(originalPartner.getWaitlistPosition());
        assertThatThrownBy(() -> service.moveToMain(tournament.getId(), original.getPlayer().getId()))
                .isInstanceOf(com.padle.core.padelcoreservice.exception.InvalidStateException.class);
        assertThat(tournament.getCupoMax()).isEqualTo(1);
    }

    @Test
    void parallelPairFormationRespectsOneSlot() throws Exception {
        var tournament = tournament(1);
        tournament.setModalidad(Modalidad.DOBLES);
        long eventId = tournament.getId();
        long first = registration(tournament, RegistrationStatus.SOLO_SEARCH).getId();
        long firstPartner = registration(tournament, RegistrationStatus.SOLO_ADD_LATER).getId();
        long second = registration(tournament, RegistrationStatus.SOLO_SEARCH).getId();
        long secondPartner = registration(tournament, RegistrationStatus.SOLO_ADD_LATER).getId();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var a = executor.submit(() -> {
                start.await();
                return doubles.adminPairSoloPlayers(eventId, first, firstPartner);
            });
            var b = executor.submit(() -> {
                start.await();
                return doubles.adminPairSoloPlayers(eventId, second, secondPartner);
            });
            start.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(RegistrationStatus.CONFIRMED, RegistrationStatus.WAITLIST);
            assertThat(registrations.countConfirmedPairs(eventId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
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

    @Test
    void dashboardShowsUnlistedPairTournamentOnlyToItsParticipants() throws Exception {
        var hidden = tournament(4);
        hidden.setTipo(TournamentType.AMERICANO_TEAMS);
        hidden.setModalidad(Modalidad.DOBLES);
        hidden.setVisibilidad(TournamentVisibility.SOLO_POR_ENLACE);
        var first = registration(hidden, RegistrationStatus.CONFIRMED);
        var second = registration(hidden, RegistrationStatus.CONFIRMED);
        pair(first, second);
        var outsider = registration(tournament(4), RegistrationStatus.CONFIRMED);
        String tournamentIdField = "\"id\":" + hidden.getId() + ",";

        for (var member : List.of(first, second)) {
            var dashboard = mvc.perform(get("/players/dashboard")
                            .param("cancelPair", hidden.getId().toString()).with(user(member.getPlayer())))
                    .andExpect(status().isOk()).andExpect(view().name("players/dashboard"))
                    .andReturn().getModelAndView();
            assertThat(dashboard.getModel().get("tournamentsJson").toString()).contains(tournamentIdField);
            assertThat(dashboard.getModel().get("myTournamentIdsJson")).isEqualTo("[" + hidden.getId() + "]");
        }

        var dashboard = mvc.perform(get("/players/dashboard")
                        .param("cancelPair", hidden.getId().toString()).with(user(outsider.getPlayer())))
                .andExpect(status().isOk()).andExpect(view().name("players/dashboard"))
                .andReturn().getModelAndView();
        assertThat(dashboard.getModel().get("tournamentsJson").toString()).doesNotContain(tournamentIdField);
        assertThat(dashboard.getModel().get("myTournamentIdsJson"))
                .isEqualTo("[" + outsider.getTournament().getId() + "]");
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
        TournamentRegistration first = registration(tournament, RegistrationStatus.CONFIRMED);
        TournamentRegistration second = registration(tournament, RegistrationStatus.CONFIRMED);
        pair(first,second);
        service.adminRemovePlayerRegistration(tournament.getId(),first.getPlayer().getId());
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
                    assertThat(row.getCancellationVersion()).isEqualTo(1L);
                });
        assertThat(payments.findById(payment.getId()).orElseThrow().getRegistration().getId()).isEqualTo(first.getId());
        assertThat(report.export(tournament.getId())).contains("original-source");
        service.cancelPairRegistration(tournament.getId(),first.getPlayer().getId(),null);
        assertThat(cancellationHistory.forPlayer(first.getPlayer(),List.of(tournament.getId()))).hasSize(2);
        assertThat(registrations.findById(first.getId()).orElseThrow().getCancellationVersion()).isEqualTo(2L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void adminFormsPairWithReserveSoloAndRespectsExistingQueue(boolean olderPairWaiting) throws Exception {
        var tournament = tournament(1);
        tournament.setTipo(TournamentType.AMERICANO_TEAMS);
        tournament.setModalidad(Modalidad.DOBLES);
        var reserve = registration(tournament, RegistrationStatus.WAITLIST);
        reserve.setPartnerActionToken("test-reserve-token");
        var search = registration(tournament, RegistrationStatus.SOLO_SEARCH);

        if (olderPairWaiting) {
            var first = registration(tournament, RegistrationStatus.WAITLIST);
            var second = registration(tournament, RegistrationStatus.WAITLIST);
            pair(first, second);
            var older = LocalDateTime.now().minusDays(1);
            first.setQueuedAt(older);
            second.setQueuedAt(older);
            reserve.setWaitlistPosition(2);
        }

        var admin = Owner.builder().email("admin@example.test").role(OwnerRole.SUPER_ADMIN).build();
        String path = "/admin/tournaments/" + tournament.getId();
        mvc.perform(get(path).with(user(admin)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-reg-id=\"" + reserve.getId() + "\"")))
                .andExpect(content().string(containsString("data-reg-id=\"" + search.getId() + "\"")));

        var otherTournament = tournament(2);
        mvc.perform(post("/admin/tournaments/" + otherTournament.getId() + "/pair-solo")
                        .param("regId1", reserve.getId().toString()).param("regId2", search.getId().toString())
                        .with(user(admin)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(false));

        mvc.perform(post(path + "/pair-solo").param("regId1", reserve.getId().toString())
                        .param("regId2", search.getId().toString()).with(user(admin)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        entities.clear();

        var first = registrations.findById(reserve.getId()).orElseThrow();
        var second = registrations.findById(search.getId()).orElseThrow();
        var expected = olderPairWaiting ? RegistrationStatus.WAITLIST : RegistrationStatus.CONFIRMED;
        assertThat(first.getStatus()).isEqualTo(expected);
        assertThat(second.getStatus()).isEqualTo(expected);
        assertThat(first.getPartner().getId()).isEqualTo(second.getPlayer().getId());
        assertThat(second.getPartner().getId()).isEqualTo(first.getPlayer().getId());
        assertThat(first.getPairBookingId()).isNotNull().isEqualTo(second.getPairBookingId());
        assertThat(first.getPartnerActionToken()).isNull();
        assertThat(registrations.countConfirmedPairs(tournament.getId())).isEqualTo(1);
        if (olderPairWaiting) {
            assertThat(first.getWaitlistPosition()).isEqualTo(1).isEqualTo(second.getWaitlistPosition());
            assertThat(first.getPosition()).isNull();
        } else {
            assertThat(first.getWaitlistPosition()).isNull();
            assertThat(first.getPosition()).isEqualTo(second.getPosition()).isEqualTo(1);
        }
    }

    @Test
    void teamPlayoffManualAddRestoresCancelledRowsWithTheirPaymentAndAttribution() throws Exception {
        var tournament = tournament(4);
        tournament.setTipo(TournamentType.AMERICANO_TEAMS);
        tournament.setModalidad(Modalidad.DOBLES);
        var first = registration(tournament, RegistrationStatus.WAITLIST);
        var second = registration(tournament, RegistrationStatus.SOLO_SEARCH);
        var oldPartner = registration(tournament, RegistrationStatus.CONFIRMED);
        oldPartner.setMainPlayerId(first.getPlayer().getId());
        oldPartner.setIsDoubleRegistration(true);
        oldPartner.setPartner(first.getPlayer());
        oldPartner.cancel("Old pair");
        var payment = payments.saveAndFlush(Payment.builder().registration(first)
                .amount(new BigDecimal("1000")).currency("ARS").status(PaymentStatus.PAID).build());
        jdbc.update("UPDATE marketing_registrations_db SET first_touch = 'team-playoff-source' WHERE registration_id = ?",
                first.getId());
        service.adminRemovePlayerRegistration(tournament.getId(), first.getPlayer().getId());
        service.adminRemovePlayerRegistration(tournament.getId(), second.getPlayer().getId());

        var admin = Owner.builder().email("admin@example.test").role(OwnerRole.SUPER_ADMIN).build();
        String path = "/tournaments/team-playoff/admin/" + tournament.getId();
        mvc.perform(post(path + "/teams/add").param("player1Id", first.getPlayer().getId().toString())
                        .param("player2Id", second.getPlayer().getId().toString()).with(user(admin)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path))
                .andExpect(flash().attributeExists("success"));
        entities.flush();
        entities.clear();

        assertThat(registrations.findByTournamentId(tournament.getId())).hasSize(3);
        assertThat(registrations.findById(first.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registrations.findById(second.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registrations.findById(oldPartner.getId()).orElseThrow().getStatus())
                .isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(registrations.findById(first.getId()).orElseThrow().getCancellationVersion()).isEqualTo(1L);
        assertThat(registrations.findById(second.getId()).orElseThrow().getCancellationVersion()).isEqualTo(1L);
        assertThat(cancellationHistory.forPlayer(first.getPlayer(),List.of(tournament.getId()))).hasSize(1);
        assertThat(payments.findById(payment.getId()).orElseThrow().getRegistration().getId()).isEqualTo(first.getId());
        assertThat(report.export(tournament.getId())).contains("team-playoff-source");
        assertThat(jdbc.queryForObject("SELECT player2_id FROM americano_teams_db WHERE tournament_id = ?",
                Long.class, tournament.getId())).isEqualTo(second.getPlayer().getId());
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
        assertThat(cancellationHistory.forPlayer(registration.getPlayer(),List.of(tournament.getId()))).isEmpty();
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
        assertThat(cancellationHistory.forPlayer(confirmed.getPlayer(),List.of(tournament.getId()))).isEmpty();
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
        assertThat(cancellationHistory.forPlayer(confirmed.getPlayer(),List.of(tournament.getId()))).hasSize(1);
        verify(email, times(1)).sendRegistrationCancelledEmail(eq(confirmed.getPlayer().getEmail()), any(), any(), any(), any(), any(), any(), any());
        verify(email, times(1)).sendWaitlistAutoConfirmEmail(eq(reserve.getPlayer().getEmail()), any(), any(), any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void fullTournamentEmailsReserveUntilBothPlayersArePromoted(boolean addingPartner) {
        Tournament tournament = tournament(1);
        tournament.setTipo(TournamentType.AMERICANO);
        tournament.setModalidad(Modalidad.DOBLES);
        TournamentRegistration confirmed = registration(tournament, RegistrationStatus.CONFIRMED);
        pair(confirmed, registration(tournament, RegistrationStatus.CONFIRMED));

        PlayerPadel main = addingPartner ? registration(tournament, RegistrationStatus.WAITLIST).getPlayer() : player();
        main.setPreferredLocale("ru");
        PlayerPadel partner = player();
        partner.setPreferredLocale("en");
        var partnerDetails = PartnerRegistrationDto.builder()
                .existingUserId(partner.getId()).isExistingUser(true).build();

        var result = addingPartner
                ? doubles.addPartnerToSoloRegistration(tournament.getId(), main.getId(), partnerDetails)
                : doubles.registerForDoubleTournament(service.getTournamentDtoById(tournament.getId()).orElseThrow(),
                        main.getId(), partnerDetails);
        assertThat(result.getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
        verifyNoInteractions(email);
        TestTransaction.flagForCommit();
        TestTransaction.end();

        verify(email).sendWaitlistNotificationEmail(eq(main.getEmail()), any(), any(), any(), any(), any(),
                eq(result.getWaitlistPosition()), eq(Locale.forLanguageTag("ru")));
        verify(email).sendWaitlistNotificationEmail(eq(partner.getEmail()), any(), any(), any(), any(), any(),
                eq(result.getWaitlistPosition()), eq(Locale.ENGLISH));
        verifyNoMoreInteractions(email);

        service.cancelPairRegistration(tournament.getId(), confirmed.getPlayer().getId(), "Test cancellation");
        TestTransaction.start();
        for (PlayerPadel player : List.of(main, partner)) {
            assertThat(registrations.findByTournamentIdAndPlayerId(tournament.getId(), player.getId()).orElseThrow()
                    .getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
            verify(email).sendParticipationRequestEmail(eq(player.getEmail()), any(), any(), any(), eq(false), eq(player.getLocale()));
        }
        verify(email, never()).sendTournamentConfirmationEmail(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void readyPairImmediatelyPromotedReceivesOnlyOneConfirmationPerPlayer() {
        Tournament tournament = tournament(1);
        tournament.setTipo(TournamentType.AMERICANO);
        tournament.setModalidad(Modalidad.DOBLES);
        PlayerPadel main = registration(tournament, RegistrationStatus.WAITLIST).getPlayer();
        PlayerPadel partner = player();

        var result = doubles.addPartnerToSoloRegistration(tournament.getId(), main.getId(),
                PartnerRegistrationDto.builder().existingUserId(partner.getId()).isExistingUser(true).build());
        assertThat(result.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        verifyNoInteractions(email);
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        verify(email).sendParticipationRequestEmail(eq(main.getEmail()), any(), any(), any(), eq(false), any());
        verify(email).sendParticipationRequestEmail(eq(partner.getEmail()), any(), any(), any(), eq(false), any());
        verifyNoMoreInteractions(email);
    }

    @Test
    void onlyBookerCanConfirmForGuestAndGetDoesNotRecordAnAnswer() throws Exception {
        Tournament event = tournament(1);
        event.setTipo(TournamentType.AMERICANO);
        event.setModalidad(Modalidad.DOBLES);
        TournamentRegistration booker = registration(event, RegistrationStatus.WAITLIST);
        TournamentRegistration guest = registration(event, RegistrationStatus.WAITLIST);
        guest.getPlayer().setEmail(UUID.randomUUID() + "@1padel.guest");
        pair(booker, guest);
        booker.setPairBookedBy(booker.getPlayer().getId());
        guest.setPairBookedBy(booker.getPlayer().getId());
        service.processWaitlistForTournament(event.getId());

        var ownRequest = requestFor(booker);
        var guestRequest = requestFor(guest);
        var other = player();
        String link = "/participacion/" + guestRequest.getId();
        mvc.perform(get(link).with(user(booker.getPlayer())))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post(link).param("decision", "CONFIRM").with(user(booker.getPlayer())))
                .andExpect(redirectedUrl("/error/403"));
        assertThat(guestRequest.getStatus()).isEqualTo(ParticipationStatus.PENDING);
        assertThat(participation.list(booker.getPlayer())).hasSize(2);
        assertThatThrownBy(() -> participation.review(guestRequest.getId(), other, true))
                .isInstanceOf(AccountException.class);

        for (var request : List.of(ownRequest, guestRequest)) {
            assertThat(participation.answer(request.getId(), booker.getPlayer(), Decision.CONFIRM, true).status())
                    .isEqualTo(ParticipationStatus.CONFIRMED);
            assertThat(participation.answer(request.getId(), booker.getPlayer(), Decision.CONFIRM, true).status())
                    .isEqualTo(ParticipationStatus.CONFIRMED);
        }
        assertThat(booker.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(guest.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
    }

    @Test
    void reserveDeclineAfterCutoffRetainsPartnerPlaceUntilSecondDecline() {
        Tournament event = tournament(1);
        event.setTipo(TournamentType.AMERICANO);
        event.setModalidad(Modalidad.DOBLES);
        event.setDeadlineCancelacion(LocalDateTime.now().minusHours(1));
        TournamentRegistration first = registration(event, RegistrationStatus.WAITLIST);
        TournamentRegistration second = registration(event, RegistrationStatus.WAITLIST);
        pair(first, second);
        service.processWaitlistForTournament(event.getId());
        TournamentRegistration queued = registration(event, RegistrationStatus.WAITLIST);
        TournamentRegistration queuedPartner = registration(event, RegistrationStatus.WAITLIST);
        pair(queued, queuedPartner);

        var firstRequest = requestFor(first);
        var secondRequest = requestFor(second);
        assertThatThrownBy(() -> participation.review(secondRequest.getId(), first.getPlayer(), false))
                .isInstanceOf(AccountException.class);
        assertThat(participation.answer(firstRequest.getId(), first.getPlayer(), Decision.CONFIRM, false).status())
                .isEqualTo(ParticipationStatus.CONFIRMED);
        assertThat(participation.answer(firstRequest.getId(), first.getPlayer(), Decision.DECLINE, false).status())
                .isEqualTo(ParticipationStatus.DECLINED);
        assertThat(second.needsReplacementInMain()).isTrue();
        assertThat(queued.getStatus()).isEqualTo(RegistrationStatus.WAITLIST);

        assertThat(participation.answer(secondRequest.getId(), second.getPlayer(), Decision.DECLINE, false).status())
                .isEqualTo(ParticipationStatus.DECLINED);
        assertThat(queued.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(queuedPartner.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(requestFor(queued).getStatus()).isEqualTo(ParticipationStatus.PENDING);
    }

    @Test
    void replacementReceivesNewRequestAndRemainingPlayerKeepsTheirAnswer() {
        Tournament event = tournament(1);
        event.setTipo(TournamentType.AMERICANO);
        event.setModalidad(Modalidad.DOBLES);
        TournamentRegistration remaining = registration(event, RegistrationStatus.WAITLIST);
        TournamentRegistration leaving = registration(event, RegistrationStatus.WAITLIST);
        pair(remaining, leaving);
        service.processWaitlistForTournament(event.getId());
        var existingRequest = requestFor(remaining);
        participation.answer(existingRequest.getId(), remaining.getPlayer(), Decision.CONFIRM, false);
        participation.answer(requestFor(leaving).getId(), leaving.getPlayer(), Decision.DECLINE, false);

        PlayerPadel newcomer = player();
        doubles.addPartnerToSoloRegistration(event.getId(), remaining.getPlayer().getId(),
                PartnerRegistrationDto.builder().existingUserId(newcomer.getId()).isExistingUser(true).build());

        var added = registrations.findByTournamentIdAndPlayerId(event.getId(), newcomer.getId()).orElseThrow();
        assertThat(added.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(requestFor(added).getTrigger()).isEqualTo(ParticipationRequest.Trigger.REPLACEMENT);
        assertThat(requestFor(added).getStatus()).isEqualTo(ParticipationStatus.PENDING);
        assertThat(requestFor(remaining).getId()).isEqualTo(existingRequest.getId());
        assertThat(requestFor(remaining).getStatus()).isEqualTo(ParticipationStatus.CONFIRMED);
    }

    @Test
    void decliningPairProposalTwicePreservesBothSoloRegistrations() {
        Tournament event = tournament(1);
        event.setTipo(TournamentType.AMERICANO);
        event.setModalidad(Modalidad.DOBLES);
        TournamentRegistration proposer = registration(event, RegistrationStatus.WAITLIST);
        TournamentRegistration target = registration(event, RegistrationStatus.WAITLIST);
        doubles.proposePair(event.getId(), proposer.getPlayer().getId(), target.getId());
        String token = target.getPartnerRegistrationToken();

        assertThat(doubles.declinePairProposal(token)).isEqualTo(PairProposalResponseRepository.Response.DECLINED);
        assertThat(doubles.declinePairProposal(token)).isEqualTo(PairProposalResponseRepository.Response.DECLINED);
        assertThat(proposer.getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(target.getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(target.getPartnerRegistrationToken()).isNull();
        assertThat(proposer.getPartner()).isNull();
        assertThat(target.getPartner()).isNull();
    }

    private ParticipationRequest requestFor(TournamentRegistration registration) {
        return participationRequests.findByRegistrationIdAndSupersededAtIsNull(registration.getId()).orElseThrow();
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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void adminRebooksPairWithoutLosingHistory(boolean withAttribution) throws Exception {
        var event = tournament(5);
        event.setModalidad(Modalidad.DOBLES);
        event.setTipo(TournamentType.AMERICANO_TEAMS);
        var first = player();
        var former = player();
        var replacement = player();
        UUID oldBooking = UUID.randomUUID();
        var original = pairMember(event, first, former, first.getId(), oldBooking);
        var oldPartner = pairMember(event, former, first, first.getId(), oldBooking);
        var previousPayment = payments.saveAndFlush(Payment.builder().registration(original).pairBookingId(oldBooking)
                .amount(BigDecimal.TEN).status(PaymentStatus.PAID).notes("PARTNER_PAYMENT").build());
        service.adminRemovePlayerRegistration(event.getId(), first.getId());
        entities.flush();
        if (withAttribution) {
            jdbc.update("UPDATE marketing_registrations_db SET first_touch = 'original' WHERE registration_id = ?", original.getId());
        } else {
            jdbc.update("DELETE FROM marketing_registrations_db WHERE registration_id = ?", original.getId());
        }
        var newPartner = withAttribution ? replacement : former;
        String body = "{\"playerIds\":[%d,%d]}".formatted(first.getId(), newPartner.getId());
        TestTransaction.flagForCommit();
        TestTransaction.end();

        String route = "/test/tournaments/" + event.getId();
        mvc.perform(get("/test/tournaments/available-players-for-pairs").param("tournamentId", event.getId().toString())
                        .with(user("admin").roles("SUPER_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.players[?(@.id == " + first.getId() + ")]").isNotEmpty());
        for (String action : List.of("/register-pair-with-players", "/register-pair")) {
            mvc.perform(post(route + action).with(user("admin").roles("SUPER_ADMIN")).with(csrf())
                            .contentType("application/json").content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tournamentStats.confirmed").value(1))
                    .andExpect(jsonPath("$.tournamentStats.totalRegistrations").value(2));
        }
        assertThat(jdbc.queryForObject("SELECT partner_id FROM tournament_registrations_db WHERE id = ?", Long.class, original.getId()))
                .isEqualTo(newPartner.getId());
        assertThat(jdbc.queryForObject("SELECT pair_booking_id FROM tournament_registrations_db WHERE id = ?", UUID.class, original.getId()))
                .isNotEqualTo(oldBooking);
        assertThat(jdbc.queryForObject("SELECT pair_booking_id FROM payments_db WHERE id = ?", UUID.class, previousPayment.getId()))
                .isEqualTo(oldBooking);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM registration_cancellation_members WHERE registration_id = ?", Integer.class, original.getId()))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT cancellation_version FROM tournament_registrations_db WHERE id = ?", Long.class, original.getId()))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT is_active FROM tournament_registrations_db WHERE id = ?", Boolean.class, oldPartner.getId()))
                .isEqualTo(!withAttribution);
        if (withAttribution) {
            assertThat(jdbc.queryForObject("SELECT first_touch FROM marketing_registrations_db WHERE registration_id = ?", String.class, original.getId()))
                    .isEqualTo("original");
        }
    }

    @Test
    void adminGuestBookingRollsBackOnFailureAndReplaysWithoutNewGuest() throws Exception {
        var event = tournament(1);
        event.setModalidad(Modalidad.DOBLES);
        event.setTipo(TournamentType.AMERICANO_TEAMS);
        var booker = player();
        var former = player();
        var original = pairMember(event, booker, former, booker.getId(), UUID.randomUUID());
        service.adminRemovePlayerRegistration(event.getId(), booker.getId());
        var guestName = "Guest-" + UUID.randomUUID();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        String route = "/test/tournaments/" + event.getId() + "/register-guest-pair";
        String payload = "{\"player1\":{\"id\":%d},\"player2\":{\"nombre\":\"%s\"}}".formatted(booker.getId(), guestName);
        String replayKey = UUID.randomUUID().toString();
        String checkName = "qa_manual_registration";
        jdbc.execute("ALTER TABLE tournament_registrations_db ADD CONSTRAINT " + checkName
                + " CHECK (tournament_id <> " + event.getId() + " OR player_id = " + booker.getId() + ") NOT VALID");
        try {
            mvc.perform(post(route).with(user("admin").roles("SUPER_ADMIN")).with(csrf())
                            .header("Idempotency-Key", replayKey).locale(Locale.ENGLISH)
                            .contentType("application/json").content(payload))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("technical"))
                    .andExpect(jsonPath("$.requestId").exists())
                    .andExpect(content().string(not(containsString(checkName))));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM player_padel_db WHERE nombre = ?", Integer.class, guestName)).isZero();
            assertThat(jdbc.queryForObject("SELECT is_active FROM tournament_registrations_db WHERE id = ?", Boolean.class, original.getId())).isFalse();
        } finally {
            jdbc.execute("ALTER TABLE tournament_registrations_db DROP CONSTRAINT " + checkName);
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(route).with(user("admin").roles("SUPER_ADMIN")).with(csrf())
                            .header("Idempotency-Key", replayKey).contentType("application/json").content(payload))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.tournamentStats.confirmed").value(1));
        }
        var guestId = jdbc.queryForObject("SELECT id FROM player_padel_db WHERE nombre = ?", Long.class, guestName);
        playerIds.add(guestId);
        assertThat(jdbc.queryForObject("SELECT partner_id FROM tournament_registrations_db WHERE id = ?", Long.class, original.getId()))
                .isEqualTo(guestId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tournament_registrations_db WHERE tournament_id = ? AND is_active",
                Integer.class, event.getId())).isEqualTo(2);
        mvc.perform(post(route).with(user("admin").roles("SUPER_ADMIN")).with(csrf())
                        .header("Idempotency-Key", replayKey).contentType("application/json").content(payload.replace(guestName, "Other")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("request_conflict"));
    }

    @Test
    void adminRebooksCanchaAbiertaAfterRemovalWithAttribution() throws Exception {
        var event = tournament(4);
        var row = registration(event, RegistrationStatus.CONFIRMED);
        service.adminRemovePlayerRegistration(event.getId(), row.getPlayer().getId());
        entities.flush();
        jdbc.update("UPDATE marketing_registrations_db SET first_touch = 'original' WHERE registration_id = ?", row.getId());
        TestTransaction.flagForCommit();
        TestTransaction.end();

        var route = "/test/tournaments/" + event.getId() + "/register-guest-player";
        var payload = "{\"id\":%d}".formatted(row.getPlayer().getId());
        mvc.perform(post(route).with(user(row.getPlayer())).with(csrf())
                        .contentType("application/json").content(payload))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT is_active FROM tournament_registrations_db WHERE id = ?", Boolean.class, row.getId())).isFalse();
        mvc.perform(post(route).with(user("admin").roles("SUPER_ADMIN")).with(csrf())
                        .contentType("application/json").content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tournamentStats.confirmed").value(1));
        assertThat(jdbc.queryForObject("SELECT is_active FROM tournament_registrations_db WHERE id = ?", Boolean.class, row.getId())).isTrue();
        assertThat(jdbc.queryForObject("SELECT first_touch FROM marketing_registrations_db WHERE registration_id = ?", String.class, row.getId()))
                .isEqualTo("original");
    }

    private TournamentRegistration pairMember(Tournament event, PlayerPadel player, PlayerPadel partner,
                                              Long mainPlayerId, UUID booking) {
        return registrations.saveAndFlush(TournamentRegistration.builder().tournament(event).player(player)
                .partner(partner).pairBookingId(booking).mainPlayerId(mainPlayerId).isDoubleRegistration(true)
                .status(RegistrationStatus.CONFIRMED).position(1).build());
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

    private PlayerPadel player() {
        PlayerPadel player = players.saveAndFlush(PlayerPadel.builder().nombre("Игрок").apellido("Отмена")
                .email(UUID.randomUUID() + "@example.test").passwordHash("test-hash").activo(true)
                .emailConfirmado(true).preferredLocale("es").nivelJugador(Nivel.C9).build());
        playerIds.add(player.getId());
        return player;
    }

    private TournamentRegistration registration(Tournament tournament, RegistrationStatus status) {
        return registrations.saveAndFlush(TournamentRegistration.builder().tournament(tournament).player(player())
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
