package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.application.identity.GuestProfileClaims;
import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.persistence.identity.PlayerRepository;
import com.padle.core.padelcoreservice.config.GuestClaimContact;
import com.padle.core.persistence.clubs.Club;
import com.padle.core.padelcoreservice.model.Match;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.Payment;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.persistence.clubs.ClubRepository;
import com.padle.core.padelcoreservice.service.RecaptchaService;
import com.padle.core.padelcoreservice.service.GuestProfileClaimService;
import com.padle.core.padelcoreservice.dto.GuestProfileClaimDtos.Approval;
import com.padle.core.padelcoreservice.dto.GuestProfileClaimDtos.Request;
import com.padle.core.padelcoreservice.dto.GuestProfileClaimDtos.ManualMerge;
import com.padle.core.application.identity.GuestProfileClaims.Verification;
import com.padle.core.application.identity.GuestProfileClaims.ContactChoice;
import com.padle.core.padelcoreservice.exception.AccountException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.padle.core.padelcoreservice.support.PlayerFixtures.playerUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.doReturn;
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
    @Autowired MatchRepository matches;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager em;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper json;
    @Autowired GuestProfileClaimService claims;
    @Autowired TransactionTemplate transaction;
    @MockitoBean RecaptchaService captcha;
    @MockitoSpyBean GuestClaimContact support;
    @MockitoSpyBean GuestProfileClaims claimRecords;

    @Test
    void phoneClaimIndexAndNormalizationWorkWithRestoreSearchPath() {
        jdbc.sql("CREATE TEMP TABLE restore_phone_probe(phone text) ON COMMIT DROP").update();
        jdbc.sql("INSERT INTO restore_phone_probe VALUES ('+54 9 11 2233-7755'), ('+7 100 000-00-01')").update();
        jdbc.sql("SET LOCAL search_path TO pg_catalog").update();
        jdbc.sql("CREATE INDEX restore_phone_probe_idx ON pg_temp.restore_phone_probe (public.player_claim_phone(phone))").update();

        assertThat(jdbc.sql("SELECT public.player_claim_phone(:phone)").param("phone", MOBILE)
                .query(String.class).single()).isEqualTo("1122337755");
        assertThat(jdbc.sql("SELECT public.player_claim_phone(:phone)").param("phone", "+7 100 000-00-01")
                .query(String.class).single()).isEqualTo("71000000001");
    }

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
    void recordsRequestWhenSupportWhatsappIsNotConfigured() throws Exception {
        doReturn(Optional.empty()).when(support).url();
        var account = player(false, null);
        mvc.perform(post(REQUEST).with(playerUser(account)).with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(new Request(MOBILE))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.number").exists())
                .andExpect(jsonPath("$.whatsappUrl").doesNotExist());
        mvc.perform(get(REQUEST).with(playerUser(account))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    void repeatedRequestKeepsItsNumberAndIsVisibleWithoutOpeningWhatsapp() throws Exception {
        var account = player(false, null);
        var other = player(false, null);
        var reference = request(account, MOBILE);
        assertThat(request(account, LOCAL)).isEqualTo(reference);

        var own = mvc.perform(get(REQUEST).with(playerUser(account)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0]").exists())
                .andExpect(jsonPath("$[0].reference").value(reference.toString()))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long number = json.readTree(own).path(0).path("number").asLong();
        mvc.perform(get(REQUEST).with(playerUser(other))).andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mvc.perform(get("/api/admin/guest-profile-claims").param("search", Long.toString(number))
                        .with(user(owner(OwnerRole.ORGANIZER))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].number").exists())
                .andExpect(jsonPath("$.items[0].accountId").exists());
        var profiles = mvc.perform(get("/api/admin/guest-profile-claims/profiles")
                        .param("search", account.getId().toString()).param("guest", "false")
                        .with(user(owner(OwnerRole.ORGANIZER))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(profiles).path(0).path("id").asLong()).isEqualTo(account.getId());
        mvc.perform(get("/api/admin/guest-profile-claims").with(user(owner(OwnerRole.CLUB_ADMIN))))
                .andExpect(status().isForbidden());
    }

    @Test
    void clarificationAndRejectionAreVisibleOnlyToTheRequester() throws Exception {
        var guest = player(true, MOBILE);
        var account = player(false, null);
        var organizer = owner(OwnerRole.ORGANIZER);
        var reference = request(account);
        mvc.perform(post(APPROVE, reference).with(user(organizer)).with(csrf())
                        .contentType("application/json").content("{\"verified\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GUEST_CLAIM_VERIFICATION_REQUIRED"));
        approve(reference, organizer, OTHER, 409, "code", "GUEST_CLAIM_PHONE_MISMATCH");
        mvc.perform(get(REQUEST).with(playerUser(account))).andExpect(jsonPath("$[0].status").value("NEEDS_DETAILS"));
        mvc.perform(post("/api/admin/guest-profile-claims/{reference}/decision", reference)
                        .with(user(organizer)).with(csrf()).contentType("application/json")
                        .content("{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        approve(reference, organizer, MOBILE, 409, "code", "GUEST_CLAIM_ALREADY_DECIDED");
        assertThat(players.findById(guest.getId()).orElseThrow().isActivo()).isTrue();
    }

    @Test
    void manualMergeUsesSameChecksAndKeepsContactUntilReplacementIsExplicit() throws Exception {
        var guest = player(true, MOBILE);
        var account = player(false, OTHER);
        var organizer = owner(OwnerRole.ORGANIZER);
        var URL = "/api/admin/guest-profile-claims/manual";
        mvc.perform(get(URL).with(user(organizer)).param("guestId", guest.getId().toString())
                        .param("accountId", account.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.differences[0]").value("PHONE_DIFFERS"));
        String payload = """
                {"guestId":%d,"accountId":%d,"verified":true,"verificationMethod":"OTHER","replacePhone":%s}
                """;
        mvc.perform(post(URL).with(user(organizer)).with(csrf()).contentType("application/json")
                        .content(payload.formatted(guest.getId(), account.getId(), false)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GUEST_CLAIM_ACCOUNT_PHONE_DIFFERS"));
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(URL).with(user(organizer)).with(csrf()).contentType("application/json")
                            .content(payload.formatted(guest.getId(), account.getId(), true)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.playerId").value(account.getId().intValue()));
        }
        assertThat(jdbc.sql("""
                SELECT initiator || ':' || verification_method FROM guest_profile_claims WHERE guest_id = :guest
                """).param("guest", guest.getId()).query(String.class).single()).isEqualTo("ADMIN:OTHER");
        assertThat(players.findById(account.getId()).orElseThrow().getTelefono()).isEqualTo(MOBILE);
    }

    @Test
    void oldServerCanSaveAndApproveAfterDatabaseMigration() throws Exception {
        var account = player(false, null);
        var guest = player(true, MOBILE);
        var original = request(account);
        var owner = owner(OwnerRole.ORGANIZER);
        mvc.perform(post("/api/admin/guest-profile-claims/{reference}/decision", original)
                        .with(user(owner)).with(csrf()).contentType("application/json")
                        .content("{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isOk());
        var legacyId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO guest_profile_claims(id, account_id, phone_key)
                VALUES (:id, :account, '1122337755')
                ON CONFLICT (account_id) WHERE approved_at IS NULL DO UPDATE
                SET id = EXCLUDED.id, phone_key = EXCLUDED.phone_key, created_at = CURRENT_TIMESTAMP
                """).param("id", legacyId).param("account", account.getId()).update();
        jdbc.sql("""
                UPDATE guest_profile_claims SET guest_id = :guest, approved_by = :owner,
                  approved_at = CURRENT_TIMESTAMP, resolved_player_id = :account WHERE id = :id
                """).param("id", legacyId).param("guest", guest.getId()).param("account", account.getId())
                .param("owner", owner.getId()).update();
        mvc.perform(get("/api/admin/guest-profile-claims/{reference}", legacyId).with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("MERGED"));
        mvc.perform(get("/api/admin/guest-profile-claims/{reference}", original).with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void serverWorksWithExistingWhatsappReviewFormDuringDeployment() throws Exception {
        player(true, MOBILE);
        var account = player(false, null);
        var reference = request(account);
        var organizer = owner(OwnerRole.ORGANIZER);
        mvc.perform(post(APPROVE, reference).with(user(organizer)).with(csrf())
                        .contentType("application/json").content("{\"whatsappSenderPhone\":\"" + LOCAL + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.playerId").value(account.getId().intValue()));
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
        jdbc.sql("INSERT INTO marketing_registrations_db(registration_id) VALUES (:id) ON CONFLICT DO NOTHING")
                .param("id", guestRow.getId()).update();
        var originalHash = account.getPasswordHash();
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
        assertThat(players.findById(account.getId()).orElseThrow().getPasswordHash()).isEqualTo(originalHash);
        assertThat(players.findById(account.getId()).orElseThrow().getEmail()).isEqualTo(account.getEmail());
        assertThat(jdbc.sql("SELECT count(*) FROM marketing_registrations_db WHERE registration_id = :id")
                .param("id", guestRow.getId()).query(Long.class).single()).isEqualTo(1);
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
                    .containsExactlyInAnyOrder("APPROVED", "GUEST_CLAIM_NOT_GUEST");
        } finally {
            executor.shutdownNow();
            transaction.executeWithoutResult(status -> {
                jdbc.sql("DELETE FROM guest_profile_claims WHERE id IN (:references)")
                        .param("references", List.of(first, second)).update();
                players.deleteAllById(fixture.stream().map(PlayerPadel::getId).toList());
            });
        }
    }

    @Test
    void manualReviewSeparatesInvalidContactFromIdentityAndPreservesMainAccount() throws Exception {
        var guest = player(true, "@FixtureHandle");
        guest.setNivelJugador(Nivel.C6);
        players.saveAndFlush(guest);
        var account = player(false, null);
        account.setTelegramUsername("@FixtureHandle");
        players.saveAndFlush(account);
        var opponent = player(false, null);
        var event = tournament();
        var registration = registration(event, guest, opponent);
        var partner = registration(event, opponent, guest);
        var guestMatch = matches.saveAndFlush(Match.builder().tournamentId(event.getId()).ronda(1).partidoNumero(1)
                .tipo(MatchType.INDIVIDUAL).player1Id(guest.getId()).player2Id(opponent.getId())
                .ganadorId(guest.getId()).set1Equipo1(6).set1Equipo2(4).estado(MatchStatus.FINALIZADO).build());
        var mainMatch = matches.saveAndFlush(Match.builder().tournamentId(tournament().getId()).ronda(1).partidoNumero(1)
                .tipo(MatchType.INDIVIDUAL).player1Id(account.getId()).player2Id(opponent.getId())
                .ganadorId(account.getId()).set1Equipo1(6).set1Equipo2(3).estado(MatchStatus.FINALIZADO).build());
        registration.setAttended(true);
        registrations.saveAndFlush(registration);
        var payment = payments.saveAndFlush(Payment.builder().registration(registration)
                .amount(new BigDecimal("1234.50")).status(PaymentStatus.PAID).build());
        var hash = account.getPasswordHash();
        var organizer = owner(OwnerRole.SUPER_ADMIN);
        String url = "/api/admin/guest-profile-claims/manual";
        mvc.perform(get(url).with(user(organizer)).param("guestId", guest.getId().toString())
                        .param("accountId", account.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.readiness").value("NEEDS_RESOLUTION"))
                .andExpect(jsonPath("$.conflicts.length()").value(0L)).andExpect(jsonPath("$.guest.guest").value(true))
                .andExpect(jsonPath("$.account.guest").value(false))
                .andExpect(jsonPath("$.account.level").value("C7"))
                .andExpect(jsonPath("$.differences[0]").value("GUEST_PHONE_INVALID"))
                .andExpect(jsonPath("$.differences[1]").value("LEVEL_DIFFERS"));
        var input = new ManualMerge(
                guest.getId(), account.getId(), true,
                Verification.OTHER, null, false);
        mvc.perform(post(url).with(user(organizer)).with(csrf()).contentType("application/json")
                        .content(json.writeValueAsString(input)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GUEST_CLAIM_CONTACT_CHOICE_REQUIRED"));
        String body = manualPayload(guest, account, "OTHER", true);
        for (int attempt = 0; attempt < 2; attempt++) {
            mvc.perform(post(url).with(user(organizer)).with(csrf()).contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.playerId").value(account.getId()));
        }
        em.flush();
        em.clear();
        var main = players.findById(account.getId()).orElseThrow();
        assertThat(main.getTelefono()).isNull();
        assertThat(main.getTelegramUsername()).isEqualTo("@FixtureHandle");
        assertThat(main.getNivelJugador()).isEqualTo(Nivel.C7);
        assertThat(main.getPasswordHash()).isEqualTo(hash);
        assertThat(main.getEmail()).isEqualTo(account.getEmail());
        assertThat(players.findById(guest.getId()).orElseThrow().isActivo()).isFalse();
        var moved = registrations.findById(registration.getId()).orElseThrow();
        assertThat(moved.getPlayer().getId()).isEqualTo(account.getId());
        assertThat(moved.getAttended()).isTrue();
        assertThat(registrations.findById(partner.getId()).orElseThrow().getPartner().getId()).isEqualTo(account.getId());
        var movedMatch = matches.findById(guestMatch.getId()).orElseThrow();
        assertThat(movedMatch.getPlayer1Id()).isEqualTo(account.getId());
        assertThat(movedMatch.getGanadorId()).isEqualTo(account.getId());
        assertThat(movedMatch.getSet1Equipo2()).isEqualTo(4);
        assertThat(matches.findById(mainMatch.getId()).orElseThrow().getSet1Equipo2()).isEqualTo(3);
        assertThat(payments.findById(payment.getId()).orElseThrow().getRegistration().getId()).isEqualTo(registration.getId());
        assertThat(jdbc.sql("SELECT count(*) FROM guest_profile_claims WHERE guest_id = :id AND approved_by = 9876 AND approved_at IS NOT NULL AND contact_resolution = 'KEEP_ACCOUNT'")
                .param("id", guest.getId()).query(Long.class).single()).isEqualTo(1L);
        mvc.perform(get(url).with(user(organizer)).param("guestId", guest.getId().toString())
                        .param("accountId", account.getId().toString()))
                .andExpect(jsonPath("$.readiness").value("COMPLETED"));
    }

    @Test
    void manualMergeKeepsExistingMainPhoneAndRequiresValidVerification() throws Exception {
        var guest = player(true, null);
        var account = player(false, OTHER);
        String url = "/api/admin/guest-profile-claims/manual";
        var admin = owner(OwnerRole.SUPER_ADMIN);
        mvc.perform(post(url).with(user(admin)).with(csrf()).contentType("application/json")
                        .content(manualPayload(guest, account, "WHATSAPP", true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GUEST_CLAIM_PHONE_VERIFICATION_UNAVAILABLE"));
        mvc.perform(post(url).with(user(admin)).with(csrf()).contentType("application/json")
                        .content(manualPayload(guest, account, "IN_PERSON", false)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GUEST_CLAIM_VERIFICATION_REQUIRED"));
        mvc.perform(post(url).with(user(owner(OwnerRole.CLUB_ADMIN))).with(csrf()).contentType("application/json")
                        .content(manualPayload(guest, account, "IN_PERSON", true))).andExpect(status().isForbidden());
        mvc.perform(post(url).with(user(admin)).contentType("application/json")
                        .content(manualPayload(guest, account, "IN_PERSON", true))).andExpect(status().isForbidden());
        mvc.perform(post(url).with(user(admin)).with(csrf()).contentType("application/json")
                        .content(manualPayload(guest, account, "IN_PERSON", true))).andExpect(status().isOk());
        assertThat(players.findById(account.getId()).orElseThrow().getTelefono()).isEqualTo(OTHER);
    }

    @Test
    void manuallySelectedProfilesStillRejectOverlappingHistory() throws Exception {
        var guest = player(true, "@NotAPhone");
        var account = player(false, null);
        var event = tournament();
        var one = registration(event, guest, null);
        var two = registration(event, account, null);
        String url = "/api/admin/guest-profile-claims/manual";
        var admin = owner(OwnerRole.SUPER_ADMIN);
        mvc.perform(get(url).with(user(admin)).param("guestId", guest.getId().toString())
                        .param("accountId", account.getId().toString()))
                .andExpect(jsonPath("$.readiness").value("FORBIDDEN"))
                .andExpect(jsonPath("$.conflicts[0]").value("GUEST_CLAIM_HISTORY_CONFLICT"))
                .andExpect(jsonPath("$.conflicts[1]").value("TOURNAMENT:" + event.getId()));
        mvc.perform(post(url).with(user(admin)).with(csrf()).contentType("application/json")
                        .content(manualPayload(guest, account, "OTHER", true)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GUEST_CLAIM_HISTORY_CONFLICT"));
        assertThat(registrations.findById(one.getId()).orElseThrow().getPlayer().getId()).isEqualTo(guest.getId());
        assertThat(registrations.findById(two.getId()).orElseThrow().getPlayer().getId()).isEqualTo(account.getId());
        assertThat(players.findById(guest.getId()).orElseThrow().isActivo()).isTrue();
    }

    @Test
    void phoneSearchExplainsMissingAmbiguousAndNonGuestMatchesSeparately() throws Exception {
        var account = player(false, null);
        var reference = request(account);
        var admin = owner(OwnerRole.SUPER_ADMIN);
        String url = "/api/admin/guest-profile-claims/{reference}";
        mvc.perform(get(url, reference).with(user(admin)))
                .andExpect(jsonPath("$.conflicts[0]").value("GUEST_CLAIM_GUEST_NOT_FOUND"));
        var ordinary = player(false, MOBILE);
        mvc.perform(get(url, reference).with(user(admin)))
                .andExpect(jsonPath("$.conflicts[0]").value("GUEST_CLAIM_NOT_GUEST"));
        var guest = player(true, LOCAL);
        mvc.perform(get(url, reference).with(user(admin)))
                .andExpect(jsonPath("$.conflicts[0]").value("GUEST_CLAIM_PHONE_AMBIGUOUS"));
        mvc.perform(get("/api/admin/guest-profile-claims/manual").with(user(admin))
                        .param("guestId", ordinary.getId().toString()).param("accountId", account.getId().toString()))
                .andExpect(jsonPath("$.conflicts[0]").value("GUEST_CLAIM_NOT_GUEST"));
        mvc.perform(post("/api/admin/guest-profile-claims/manual").with(user(admin)).with(csrf())
                        .contentType("application/json").content(manualPayload(guest, account, "OTHER", true)))
                .andExpect(status().isOk());
        assertThat(players.findById(ordinary.getId()).orElseThrow().getTelefono()).isEqualTo(MOBILE);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void manualTransferRollsBackWhenAuditWriteFails() {
        var fixture = transaction.execute(status -> {
            var guest = player(true, "@RollbackFixture");
            var account = player(false, null);
            var event = tournament();
            var row = registration(event, guest, null);
            return List.of(guest.getId(), account.getId(), row.getId(), event.getId(), event.getClubId());
        });
        doThrow(new IllegalStateException("Synthetic audit failure")).when(claimRecords)
                .recordManual(eq(fixture.get(0)), any(), anyLong(), any(), anyLong(), any());
        try {
            var input = new ManualMerge(fixture.get(0), fixture.get(1), true,
                    Verification.OTHER, null, false, null,
                    ContactChoice.KEEP_ACCOUNT);
            assertThatThrownBy(() -> claims.manualMerge(input, 9876L))
                    .isInstanceOf(InvalidDataAccessApiUsageException.class)
                    .hasMessageContaining("Synthetic audit failure");
            assertThat(players.findById(fixture.get(0)).orElseThrow().isActivo()).isTrue();
            assertThat(players.findById(fixture.get(0)).orElseThrow().getTelefono()).isEqualTo("@RollbackFixture");
            assertThat(registrations.findById(fixture.get(2)).orElseThrow().getPlayer().getId()).isEqualTo(fixture.get(0));
            assertThat(jdbc.sql("SELECT count(*) FROM guest_profile_claims WHERE guest_id = :id")
                    .param("id", fixture.get(0)).query(Long.class).single()).isZero();
        } finally {
            reset(claimRecords);
            transaction.executeWithoutResult(status -> {
                jdbc.sql("DELETE FROM marketing_registrations_db WHERE registration_id = :id").param("id", fixture.get(2)).update();
                registrations.deleteById(fixture.get(2));
                tournaments.deleteById(fixture.get(3));
                clubs.deleteById(fixture.get(4));
                players.deleteAllById(fixture.subList(0, 2));
            });
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void parallelManualRequestsWithoutPhoneMergeOnlyOnce() throws Exception {
        var fixture = transaction.execute(status -> List.of(player(true, null).getId(), player(false, null).getId()));
        var input = new ManualMerge(fixture.get(0), fixture.get(1), true,
                Verification.IN_PERSON, null, false, null,
                ContactChoice.KEEP_ACCOUNT);
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var one = executor.submit(() -> { start.await(); return claims.manualMerge(input, 9876L); });
            var two = executor.submit(() -> { start.await(); return claims.manualMerge(input, 9876L); });
            start.countDown();
            assertThat(one.get(15, TimeUnit.SECONDS).playerId()).isEqualTo(fixture.get(1));
            assertThat(two.get(15, TimeUnit.SECONDS).playerId()).isEqualTo(fixture.get(1));
            assertThat(jdbc.sql("SELECT count(*) FROM guest_profile_claims WHERE guest_id = :id")
                    .param("id", fixture.get(0)).query(Long.class).single()).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
            transaction.executeWithoutResult(status -> {
                jdbc.sql("DELETE FROM guest_profile_claims WHERE guest_id = :id").param("id", fixture.get(0)).update();
                players.deleteAllById(fixture);
            });
        }
    }

    private String manualPayload(PlayerPadel guest, PlayerPadel account, String method, boolean verified) {
        return """
                {"guestId":%d,"accountId":%d,"verified":%s,"verificationMethod":"%s","contactChoice":"KEEP_ACCOUNT"}
                """.formatted(guest.getId(), account.getId(), verified, method);
    }

    private String approveInTransaction(UUID reference) {
        try {
            claims.approve(reference, new Approval(MOBILE, true, null, null, false), 9876L);
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
        return "{\"whatsappSenderPhone\":\"" + phone + "\",\"verified\":true}";
    }

    private UUID request(PlayerPadel account) throws Exception {
        return request(account, LOCAL);
    }

    private UUID request(PlayerPadel account, String phone) throws Exception {
        String body = mvc.perform(post(REQUEST).with(playerUser(account)).with(csrf()).contentType("application/json")
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
        var club = clubs.save(Club.builder().nombre("Club de prueba " + UUID.randomUUID()).direccion("Av. Test 123").isActive(true).build());
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
