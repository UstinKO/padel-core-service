package com.padle.core.padelcoreservice.receipts;

import com.padle.core.padelcoreservice.support.OwnerFixtures;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.persistence.clubs.Club;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.receipts.ReceiptModels.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.persistence.clubs.ClubRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.padle.core.padelcoreservice.telegram.PlayerTelegramReceipts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import static com.padle.core.padelcoreservice.support.PlayerFixtures.playerUser;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"telegram.enabled=false", "app.scheduling.enabled=false",
        "telegram.players.enabled=true", "telegram.players.pilot-enabled=false", "telegram.players.receipts.enabled=true",
        "telegram.players.receipts.pilot-enabled=false"})
@AutoConfigureMockMvc
@Transactional
class ReceiptIntegrationTest {
    @MockitoBean ReceiptProperties receiptProperties;
    @Autowired ReceiptIntake intake;
    @Autowired ReceiptStore store;
    @Autowired ReceiptReviewService reviews;
    @Autowired TournamentRepository events;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired PlayerRepository players;
    @Autowired OwnerFixtures owners;
    @Autowired ClubRepository clubs;
    @Autowired PaymentRepository payments;
    @Autowired MockMvc mvc;
    @Autowired PlayerTelegramReceipts botReceipts;
    private static final File FILE = new File("application/pdf", "%PDF-1.7\nsynthetic receipt".getBytes(StandardCharsets.US_ASCII));

    @BeforeEach void generalReceiptScopeForExistingScenarios() {
        when(receiptProperties.enabled()).thenReturn(true);
        when(receiptProperties.pilotEnabled()).thenReturn(false);
    }

    @Test void independentPilotProtectsButtonsUploadsAndAttachingAfterScopeChanges() {
        var owner = owner(); var allowed = row(owner); var outsider = row(owner);
        when(receiptProperties.pilotEnabled()).thenReturn(true);
        when(receiptProperties.pilotPlayerId()).thenReturn(allowed.getPlayer().getId());
        when(receiptProperties.pilotTournamentId()).thenReturn(allowed.getTournament().getId());
        assertThat(botReceipts.buttons(allowed, allowed.getPlayer().getId(), allowed.getPlayer().getLocale())).hasSize(1);
        assertThat(botReceipts.buttons(outsider, outsider.getPlayer().getId(), outsider.getPlayer().getLocale())).isEmpty();
        assertThatThrownBy(() -> intake.receive(11901, outsider.getPlayer().getId(), 34567, "fixture-file"))
                .isInstanceOf(AccountException.class);
        var upload = intake.receive(11902, allowed.getPlayer().getId(), 34567, "fixture-file");
        when(receiptProperties.pilotTournamentId()).thenReturn(outsider.getTournament().getId());
        assertThatThrownBy(() -> intake.attach(upload.id(), allowed.getPlayer().getId(), 34567,
                allowed.getId(), allowed.getCancellationVersion(), FILE)).isInstanceOf(AccountException.class);
        assertThat(store.list(allowed.getTournament().getId())).isEmpty();
        when(receiptProperties.pilotTournamentId()).thenReturn(allowed.getTournament().getId());
        var receipt = intake.attach(upload.id(), allowed.getPlayer().getId(), 34567,
                allowed.getId(), allowed.getCancellationVersion(), FILE);
        assertThat(receipt.state()).isEqualTo(State.PENDING);
        assertThat(payments.findByRegistrationId(allowed.getId())).isEmpty();
    }

    private Owner owner() {
        return owners.saveAndFlush(Owner.builder().email(UUID.randomUUID()+"@example.test").password("fixture")
                .firstName("Учебный").lastName("Организатор").role(OwnerRole.ORGANIZER).isActive(true).build());
    }
    private TournamentRegistration row(Owner owner) {
        var club = clubs.saveAndFlush(Club.builder().nombre("Учебный клуб " + UUID.randomUUID()).isActive(true).build());
        var event = events.saveAndFlush(Tournament.builder().nombre("Учебное мероприятие").ownerId(owner.getId()).clubId(club.getId())
                .fechaInicio(LocalDate.now().plusDays(1)).horaInicio(LocalTime.NOON).tipo(TournamentType.CANCHA_ABIERTA)
                .modalidad(Modalidad.INDIVIDUAL).generoFormato(GenderFormat.MIXTO).categoriaNivel(Nivel.TODOS)
                .cupoMax(8).precio(new BigDecimal("1000")).moneda("ARS").estado(TournamentStatus.REGISTRO_ABIERTO)
                .contactoOrganizador("fixture@example.test").build());
        var player = players.saveAndFlush(PlayerPadel.builder().nombre("Учебный").apellido("Игрок")
                .email(UUID.randomUUID()+"@example.test").passwordHash("fixture").nivelJugador(Nivel.C7)
                .activo(true).emailConfirmado(true).preferredLocale("ru").build());
        return registrations.saveAndFlush(TournamentRegistration.builder().tournament(event).player(player)
                .status(RegistrationStatus.CONFIRMED).isActive(true).position(1).build());
    }
    private Receipt receipt(TournamentRegistration row, long update) {
        var upload = intake.receive(update,row.getPlayer().getId(),34567,"fixture-file");
        return intake.attach(upload.id(),row.getPlayer().getId(),34567,row.getId(),row.getCancellationVersion(),FILE);
    }
    private String url(TournamentRegistration row, UUID receipt) {
        return "/api/admin/tournaments/"+row.getTournament().getId()+"/receipts/"+receipt;
    }

    @Test void receivingAndDeduplicatingAFileNeverConfirmsPayment() {
        var owner = owner(); var row = row(owner);
        var first = receipt(row,11001); var second = receipt(row,11002);
        assertThat(first.id()).isEqualTo(second.id());
        assertThat(store.list(row.getTournament().getId())).hasSize(1);
        assertThat(first.state()).isEqualTo(State.PENDING);
        assertThat(payments.findByRegistrationId(row.getId())).isEmpty();
    }
    @Test void approvalCreatesOnePaymentAndKeepsAttendanceAndParticipation() throws Exception {
        var owner = owner(); var row = row(owner);
        row.setAttended(true); row.setParticipationConfirmed(true); registrations.saveAndFlush(row);
        var receipt = receipt(row,11003);
        String payload = "{\"amount\":1000,\"currency\":\"ARS\",\"method\":\"BANK_TRANSFER\"}";
        for (int repeat = 0; repeat < 2; repeat++) {
            mvc.perform(post(url(row,receipt.id())+"/accept").with(user(owner)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("ACCEPTED"));
        }
        assertThat(payments.findByRegistrationId(row.getId())).singleElement().satisfies(payment -> {
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(payment.getAmount()).isEqualByComparingTo("1000");
        });
        assertThat(registrations.findById(row.getId()).orElseThrow().getAttended()).isTrue();
        assertThat(row.getParticipationConfirmed()).isTrue();
    }
    @Test void anOldCycleCannotPayANewRegistration() {
        var owner = owner(); var row = row(owner); var receipt = receipt(row,11004);
        row.setCancellationVersion(1L); registrations.saveAndFlush(row);
        assertThatThrownBy(() -> reviews.accept(owner,row.getTournament().getId(),receipt.id(),
                new Accept(new BigDecimal("1000"),"ARS",PaymentMethod.BANK_TRANSFER))).isInstanceOf(AccountException.class);
        assertThat(payments.findByRegistrationId(row.getId())).isEmpty();
    }
    @Test void rejectingAReceiptDoesNotCreatePaymentAndRetainsTheReason() {
        var owner = owner(); var row = row(owner); var receipt = receipt(row,11005);
        var rejected = reviews.reject(owner,row.getTournament().getId(),receipt.id(),new Reject("Нужен итоговый чек"));
        assertThat(rejected.state()).isEqualTo(State.REJECTED);
        assertThat(rejected.rejectionReason()).isEqualTo("Нужен итоговый чек");
        assertThat(payments.findByRegistrationId(row.getId())).isEmpty();
    }
    @Test void fileAndReviewRequireTheCorrectOrganizerAndCsrf() throws Exception {
        var owner = owner(); var other = owner(); var row = row(owner); var receipt = receipt(row,11006);
        mvc.perform(get(url(row,receipt.id())+"/file").with(user(other))).andExpect(status().isForbidden());
        mvc.perform(get(url(row,receipt.id())+"/file").with(playerUser(row.getPlayer()))).andExpect(status().isForbidden());
        mvc.perform(post(url(row,receipt.id())+"/reject").with(user(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"fixture\"}")).andExpect(status().isForbidden());
        mvc.perform(get(url(row,receipt.id())+"/file").with(user(owner))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andExpect(content().bytes(FILE.content()));
    }
    @Test void aForeignPlayerCannotUseAnUploadChoice() {
        var owner = owner(); var row = row(owner); var foreign = row(owner);
        var upload = intake.receive(11007,row.getPlayer().getId(),34567,"fixture-file");
        assertThatThrownBy(() -> intake.attach(upload.id(),foreign.getPlayer().getId(),34567,
                row.getId(),row.getCancellationVersion(),FILE)).isInstanceOf(AccountException.class);
        assertThat(store.list(row.getTournament().getId())).isEmpty();
    }

    @Test void paymentPageRendersTheRealReceiptFragmentWithLocalizedLabels() throws Exception {
        var owner = owner(); var row = row(owner);
        mvc.perform(get("/admin/tournaments/"+row.getTournament().getId()+"/payments?lang=ru").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"payment-receipts\"")))
                .andExpect(content().string(containsString("Чеки участников")))
                .andExpect(content().string(containsString("Подтвердить оплату")))
                .andExpect(content().string(containsString("data-receipt-registration=\""+row.getId()+"\"")));
    }
}
