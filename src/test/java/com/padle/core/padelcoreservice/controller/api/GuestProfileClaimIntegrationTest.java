package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.Payment;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.padelcoreservice.service.RecaptchaService;
import com.padle.core.padelcoreservice.service.GuestProfileClaimService;
import com.padle.core.padelcoreservice.dto.GuestProfileClaimDtos.Approval;
import com.padle.core.padelcoreservice.dto.GuestProfileClaimDtos.Request;
import com.padle.core.padelcoreservice.exception.AccountException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"recaptcha.enabled=false", "app.security.whitelisted-ips=127.0.0.1", "app.auth.google-enabled=false",
        "app.guest-claim.whatsapp-url=https://wa.me/12025550100"})
@AutoConfigureMockMvc
@Transactional
class GuestProfileClaimIntegrationTest {
    private static final String MOBILE = "+54 9 11 2233-7755";
    private static final String LOCAL = "011 15 2233-7755";
    private static final String OTHER = "+54 9 11 2233-7766";
    private static final String APPROVE = "/api/admin/guest-profile-claims/{reference}/approve";
    private static final String ACTIVATE = "/api/admin/players/{id}/activate-guest-profile";
    private static final String REQUEST = "/api/players/me/guest-profile-claims";

    @Autowired MockMvc mvc;
    @Autowired PlayerRepository players;
    @Autowired TournamentRepository tournaments;
    @Autowired ClubRepository clubs;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired PaymentRepository payments;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager em;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper json;
    @Autowired GuestProfileClaimService claims;
    @Autowired TransactionTemplate transaction;
    @MockitoBean RecaptchaService captcha;

    @ParameterizedTest
    @CsvSource({MOBILE + ", " + LOCAL, "+7 100 000-00-01, 007 100 000 00 01"})
    void duplicateSignupOffersWhatsAppWithoutExposingGuest(String storedPhone, String signupPhone) throws Exception {
        var guest = player(true, storedPhone);
        when(captcha.verify(null)).thenReturn(true);
        String email = UUID.randomUUID() + "@gmail.com";
        String body = """
                {"nombre":"Maria","apellido":"Garcia","email":"%s","telefono":"%s",
                 "nivelJugador":"C7","password":"Fixture-Password1!","confirmPassword":"Fixture-Password1!"}
                """.formatted(email, signupPhone);
        var result = mvc.perform(post("/api/players/registro").with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PHONE_IN_USE"))
                .andExpect(jsonPath("$.whatsappUrl").value("https://wa.me/12025550100"))
                .andReturn().getResponse().getContentAsString();
        assertThat(result).doesNotContain(guest.getEmail(), storedPhone);
        assertThat(players.existsByEmail(email)).isFalse();
    }

    @Test
    void activationKeepsOriginalRegistrationAndRejectsOldCredentials() throws Exception {
        var guest = player(true, MOBILE);
        var registration = registration(tournament(), guest, null);
        String oldEmail = guest.getEmail();
        String email = UUID.randomUUID() + "@gmail.com";
        var organizer = owner(OwnerRole.ORGANIZER);
        String body = """
                {"whatsappSenderPhone":"%s","email":"%s"}
                """.formatted(LOCAL, email);
        for (int i = 0; i < 2; i++) {
            mvc.perform(post(ACTIVATE, guest.getId()).with(user(organizer)).with(csrf())
                            .contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.playerId").value(guest.getId()));
        }
        em.flush();
        em.clear();
        var activated = players.findById(guest.getId()).orElseThrow();
        assertThat(activated.getEmail()).isEqualTo(email);
        assertThat(encoder.matches("guest-old-password", activated.getPasswordHash())).isFalse();
        assertThat(players.findByEmail(oldEmail)).isEmpty();
        assertThat(registrations.findById(registration.getId()).orElseThrow().getPlayer().getId()).isEqualTo(guest.getId());
    }

    @ParameterizedTest
    @CsvSource({MOBILE + ", " + LOCAL, "+7 (100) 000-00-01, 007 100 000 00 01"})
    void linksExistingAccountKeepingRegistrationPaymentAndPartner(String storedPhone, String requestPhone) throws Exception {
        var guest = player(true, storedPhone);
        var account = player(false, null);
        var booker = player(false, OTHER);
        var event = tournament();
        var guestRow = registration(event, guest, booker);
        var bookerRow = registration(event, booker, guest);
        var payment = payments.saveAndFlush(Payment.builder().registration(guestRow)
                .amount(new BigDecimal("1234.50")).status(PaymentStatus.PAID).build());
        var reference = request(account, requestPhone);
        var organizer = owner(OwnerRole.ORGANIZER);

        mvc.perform(get("/api/admin/guest-profile-claims/{reference}", reference).with(user(organizer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountEmail").value(account.getEmail()));
        approve(reference, organizer, storedPhone, 200, "playerId", account.getId().intValue());
        approve(reference, organizer, requestPhone, 200, "playerId", account.getId().intValue());

        em.flush();
        em.clear();
        assertThat(players.findById(guest.getId()).orElseThrow().isActivo()).isFalse();
        assertThat(players.findById(account.getId()).orElseThrow().getTelefono()).isEqualTo(storedPhone);
        var moved = registrations.findById(guestRow.getId()).orElseThrow();
        assertThat(moved.getPlayer().getId()).isEqualTo(account.getId());
        assertThat(moved.getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(registrations.findById(bookerRow.getId()).orElseThrow().getPartner().getId()).isEqualTo(account.getId());
        assertThat(payments.findById(payment.getId()).orElseThrow().getRegistration().getId()).isEqualTo(guestRow.getId());
        assertThat(jdbc.sql("SELECT guest_claim_canonical_id(:id)").param("id", guest.getId())
                .query(Long.class).single()).isEqualTo(account.getId());
        var snapshot = jdbc.sql("""
                SELECT guest_claim_conduct_snapshot(jsonb_build_object('americano_teams_db',
                    jsonb_build_array(jsonb_build_object('player1_id', CAST(:guest AS bigint), 'id', 123))))::text
                """).param("guest", guest.getId()).query(String.class).single();
        assertThat(json.readTree(snapshot).path("americano_teams_db").get(0).path("player1_id").asLong())
                .isEqualTo(account.getId());
    }

    @Test
    void keepsCountryCodeWhenNationalDigitsCoincide() throws Exception {
        var internationalGuest = player(true, "+7 100 000-00-01");
        var argentineGuest = player(true, "+54 100 000-00-01");
        var account = player(false, null);
        var organizer = owner(OwnerRole.ORGANIZER);
        var reference = request(account, "71000000001");

        approve(reference, organizer, argentineGuest.getTelefono(), 409, "code", "GUEST_CLAIM_PHONE_MISMATCH");
        approve(reference, organizer, internationalGuest.getTelefono(), 200, "playerId", account.getId().intValue());

        assertThat(players.findById(argentineGuest.getId()).orElseThrow().isActivo()).isTrue();
    }

    @Test
    void refusesOverlappingTournamentAndKeepsBothRegistrations() throws Exception {
        var guest = player(true, MOBILE);
        var account = player(false, null);
        var event = tournament();
        var guestRow = registration(event, guest, null);
        var accountRow = registration(event, account, null);

        approve(request(account), owner(OwnerRole.SUPER_ADMIN), MOBILE, 409, "code", "GUEST_CLAIM_HISTORY_CONFLICT");

        assertThat(registrations.findById(guestRow.getId()).orElseThrow().getPlayer().getId()).isEqualTo(guest.getId());
        assertThat(registrations.findById(accountRow.getId()).orElseThrow().getPlayer().getId()).isEqualTo(account.getId());
    }

    @Test
    void refusesWrongSenderAndRequiresOrganizerAndCsrf() throws Exception {
        var guest = player(true, MOBILE);
        var account = player(false, null);
        var reference = request(account);
        approve(reference, owner(OwnerRole.CLUB_ADMIN), MOBILE, 403, "code", "ACCESS_DENIED");
        mvc.perform(post(APPROVE, reference).with(user(owner(OwnerRole.ORGANIZER)))
                        .header("Authorization", "Bearer unverified")
                        .contentType("application/json").content(approval(MOBILE)))
                .andExpect(status().isForbidden());
        approve(reference, owner(OwnerRole.ORGANIZER), OTHER, 409, "code", "GUEST_CLAIM_PHONE_MISMATCH");
        assertThat(players.findById(guest.getId()).orElseThrow().isActivo()).isTrue();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void twoAccountsCannotBothClaimTheSameGuest() throws Exception {
        var fixture = transaction.execute(status -> List.of(player(true, MOBILE), player(false, null), player(false, null)));
        var first = claims.request(fixture.get(1), new Request(MOBILE)).reference();
        var second = claims.request(fixture.get(2), new Request(LOCAL)).reference();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var start = new CountDownLatch(1);
            var one = executor.submit(() -> {
                start.await();
                return approveInTransaction(first);
            });
            var two = executor.submit(() -> {
                start.await();
                return approveInTransaction(second);
            });
            start.countDown();
            assertThat(List.of(one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("APPROVED", "GUEST_CLAIM_CONTACT_CONFLICT");
        } finally {
            executor.shutdownNow();
            transaction.executeWithoutResult(status -> {
                jdbc.sql("DELETE FROM guest_profile_claims WHERE id IN (:references)")
                        .param("references", List.of(first, second)).update();
                players.deleteAllById(fixture.stream().map(PlayerPadel::getId).toList());
            });
        }
    }

    private String approveInTransaction(UUID reference) {
        try {
            claims.approve(reference, new Approval(MOBILE), 9876L);
            return "APPROVED";
        } catch (AccountException e) {
            return e.getMessage();
        }
    }

    private void approve(UUID reference, Owner owner, String phone, int code, String key, Object value) throws Exception {
        mvc.perform(post(APPROVE, reference).with(user(owner)).with(csrf())
                        .contentType("application/json").content(approval(phone)))
                .andExpect(status().is(code)).andExpect(jsonPath("$." + key).value(value));
    }

    private String approval(String phone) {
        return "{\"whatsappSenderPhone\":\"" + phone + "\"}";
    }

    private UUID request(PlayerPadel account) throws Exception {
        return request(account, LOCAL);
    }

    private UUID request(PlayerPadel account, String phone) throws Exception {
        String body = mvc.perform(post(REQUEST).with(user(account)).with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(new Request(phone))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.whatsappUrl").value("https://wa.me/12025550100"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).path("reference").asText());
    }

    private Owner owner(OwnerRole role) {
        return Owner.builder().id(9876L).email("organizer@example.test").role(role).build();
    }

    private PlayerPadel player(boolean guest, String phone) {
        String domain = guest ? "@1padel.guest" : "@gmail.com";
        return players.saveAndFlush(PlayerPadel.builder().nombre("Maria").apellido("Garcia")
                .email(UUID.randomUUID() + domain).telefono(phone).passwordHash(encoder.encode("guest-old-password"))
                .nivelJugador(Nivel.C7).activo(true).emailConfirmado(true).preferredLocale("es").build());
    }

    private Tournament tournament() {
        var club = clubs.save(Club.builder().nombre("Club de prueba").direccion("Av. Test 123").isActive(true).build());
        return tournaments.save(Tournament.builder().clubId(club.getId()).nombre("Torneo de prueba")
                .fechaInicio(LocalDate.now().plusDays(1)).horaInicio(LocalTime.NOON)
                .generoFormato(GenderFormat.MIXTO).categoriaNivel(Nivel.C7).tipo(TournamentType.AMERICANO_TEAMS)
                .modalidad(Modalidad.DOBLES).cupoMax(8).precio(BigDecimal.ZERO)
                .estado(TournamentStatus.REGISTRO_ABIERTO).contactoOrganizador("test@example.com").isActive(true).build());
    }

    private TournamentRegistration registration(Tournament tournament, PlayerPadel player, PlayerPadel partner) {
        return registrations.saveAndFlush(TournamentRegistration.builder().tournament(tournament).player(player)
                .partner(partner).status(RegistrationStatus.WAITLIST).waitlistPosition(2).build());
    }
}
