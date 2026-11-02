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
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "app.analytics.meta.enabled=false", "app.analytics.meta.access-token=",
        "disposable-email-domains.checkers.daily-updated-domains=false",
        "disposable-email-domains.checkers.reload.enabled=false",
        "spring.mail.username=", "spring.mail.password=",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test"
})
@Transactional
class RegistrationCancellationIntegrationTest {
    @Autowired TournamentService service;
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
