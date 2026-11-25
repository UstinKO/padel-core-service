package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.SoloRoster.*;
import com.padle.core.padelcoreservice.dto.PaymentUpdateDto;
import org.junit.jupiter.params.provider.ValueSource;
import com.padle.core.padelcoreservice.exception.SoloRosterException;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.padelcoreservice.model.americano.*;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.padelcoreservice.repository.americano.*;
import com.padle.core.padelcoreservice.service.americano.TeamPlayoffService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.concurrent.*;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
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
class SoloRosterIntegrationTest {
    @Autowired SoloRosterService service;
    @Autowired TournamentService tournamentService;
    @Autowired PaymentService paymentService;
    @Autowired TeamPlayoffService conduct;
    @Autowired TournamentRepository events;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired PlayerRepository players;
    @Autowired PaymentRepository payments;
    @Autowired ClubRepository clubs;
    @Autowired OwnerRepository owners;
    @Autowired AmericanoTeamRepository teams;
    @Autowired AmericanoMatchRepository matches;
    @Autowired MatchRepository brackets;
    @Autowired AmericanoRoundRepository rounds;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;
    @Autowired ObjectMapper json;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean EmailService email;

    private Owner admin() { return owner(OwnerRole.SUPER_ADMIN); }
    private Owner owner(OwnerRole role) {
        return owners.saveAndFlush(Owner.builder().email(UUID.randomUUID()+"@example.test").password("test")
                .firstName("Учебный").lastName("Организатор").role(role).isActive(true).build());
    }
    private Tournament event(int capacity, Owner owner) {
        var club=clubs.saveAndFlush(Club.builder().nombre("Учебная площадка "+UUID.randomUUID()).isActive(true).build());
        return events.saveAndFlush(Tournament.builder().nombre("Проверка одиночных заявок")
                .ownerId(owner.getId()).clubId(club.getId()).fechaInicio(LocalDate.now().plusDays(1)).horaInicio(LocalTime.NOON)
                .tipo(TournamentType.AMERICANO_TEAMS).modalidad(Modalidad.DOBLES).generoFormato(GenderFormat.MIXTO)
                .categoriaNivel(Nivel.TODOS).cupoMax(capacity).precio(new BigDecimal("1000"))
                .moneda("ARS").estado(TournamentStatus.REGISTRO_ABIERTO).contactoOrganizador("test@example.test").build());
    }
    private PlayerPadel player() {
        return players.saveAndFlush(PlayerPadel.builder().nombre("Участник").apellido(UUID.randomUUID().toString().substring(0,8))
                .email(UUID.randomUUID()+"@example.test").passwordHash("test").activo(true).emailConfirmado(true)
                .preferredLocale("es").nivelJugador(Nivel.C7).build());
    }
    private TournamentRegistration single(Tournament event, RegistrationStatus status) {
        var row=TournamentRegistration.builder().tournament(event).player(player()).status(status)
                .pairBookingId(UUID.randomUUID()).queuedAt(LocalDateTime.now().minusHours(1)).build();
        if(status==RegistrationStatus.CONFIRMED) {
            row.setIsDoubleRegistration(true);row.setMainPlayerId(row.getPlayer().getId());row.setPosition(1);
        }
        return registrations.saveAndFlush(row);
    }
    private Payment paid(TournamentRegistration row, String currency) {
        return payments.saveAndFlush(Payment.builder().registration(row).pairBookingId(row.getPairBookingId())
                .amount(new BigDecimal("1250.50")).currency(currency).status(PaymentStatus.PAID)
                .paymentMethod(PaymentMethod.BANK_TRANSFER).transactionId("receipt-"+row.getId()).notes("Оплата участника").build());
    }
    private Command pairing(TournamentRegistration a, TournamentRegistration b) {
        return new Command(UUID.randomUUID(),Action.PAIR,a.getId(),b.getId(),null,null,false,null);
    }
    private Command checked(Tournament e, Owner o, Command c) {
        return new Command(c.requestId(),c.action(),c.registrationId(),c.partnerRegistrationId(),c.player(),c.kind(),
                c.overrideLevel(),service.preview(e.getId(),o,c).state());
    }
    private Result apply(Tournament e, Owner o, Command c) {return service.apply(e.getId(),o,checked(e,o,c));}

    @Test void paymentsPresenceIdentityAndAuditSurviveMergeAndReplay() {
        var o=admin();var e=event(2,o);var a=single(e,RegistrationStatus.SOLO_SEARCH);var b=single(e,RegistrationStatus.SOLO_ADD_LATER);
        a.setAttended(true); registrations.saveAndFlush(a);
        var pa=paid(a,"ARS");var pb=paid(b,"USD");var originalA=a.getId();var originalB=b.getId();
        var c=checked(e,o,pairing(a,b));var result=service.apply(e.getId(),o,c);
        assertThat(service.apply(e.getId(),o,c)).isEqualTo(result);
        entities.flush();entities.clear();
        a=registrations.findById(originalA).orElseThrow();b=registrations.findById(originalB).orElseThrow();
        assertThat(a.getPartner().getId()).isEqualTo(b.getPlayer().getId());
        assertThat(b.getPartner().getId()).isEqualTo(a.getPlayer().getId());
        assertThat(a.getPairBookingId()).isEqualTo(b.getPairBookingId());
        assertThat(a.getAttended()).isTrue();assertThat(b.getAttended()).isFalse();
        assertThat(registrations.findByTournamentId(e.getId())).hasSize(2);
        assertThat(registrations.countConfirmedPairs(e.getId())).isEqualTo(1);
        assertThat(payments.findByTournamentId(e.getId())).extracting(Payment::getId).containsExactlyInAnyOrder(pa.getId(),pb.getId());
        var data=paymentService.getPaymentManagementData(e.getId());
        assertThat(data).hasSize(2);assertThat(data).extracting(d->d.getCurrency()).containsExactlyInAnyOrder("ARS","USD");
        assertThat(data).allSatisfy(d->{assertThat(d.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);assertThat(d.getAmount()).isEqualByComparingTo("1250.50");});
        assertThat(service.board(e.getId(),o).singles()).isEmpty();
        assertThat(service.board(e.getId(),o).history()).singleElement().satisfies(h->{assertThat(h.actorId()).isEqualTo(o.getId());assertThat(h.before()).contains(originalA.toString(),originalB.toString());});
        verify(email,never()).sendRegistrationCancelledEmail(any(),any(),any(),any(),any(),any(),any(),any());
    }

    @Test void reservePairDoesNotJumpEarlierCompletePairAndBothPaymentsRemain() {
        var o=admin();var e=event(1,o);
        var occupiedA=single(e,RegistrationStatus.CONFIRMED);var occupiedB=single(e,RegistrationStatus.SOLO_SEARCH);
        apply(e,o,pairing(occupiedA,occupiedB));
        var earlyA=single(e,RegistrationStatus.SOLO_SEARCH);var earlyB=single(e,RegistrationStatus.SOLO_ADD_LATER);
        earlyA.setQueuedAt(LocalDateTime.now().minusDays(2));registrations.saveAndFlush(earlyA);
        apply(e,o,pairing(earlyA,earlyB));
        var lateA=single(e,RegistrationStatus.SOLO_SEARCH);var lateB=single(e,RegistrationStatus.SOLO_SEARCH);
        paid(lateA,"ARS");paid(lateB,"ARS");
        assertThat(apply(e,o,pairing(lateA,lateB)).status()).isEqualTo("WAITLIST");
        assertThat(registrations.countConfirmedPairs(e.getId())).isEqualTo(1);
        occupiedA.setPartner(null);occupiedA.setPartnerFirstName(null);occupiedA.setPartnerLastName(null);
        occupiedB.setIsActive(false);occupiedB.setStatus(RegistrationStatus.CANCELLED);registrations.saveAllAndFlush(List.of(occupiedA,occupiedB));
        apply(e,o,new Command(UUID.randomUUID(),Action.REMOVE,occupiedA.getId(),null,null,null,false,null));
        assertThat(earlyA.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(lateA.getStatus()).isEqualTo(RegistrationStatus.WAITLIST);
        assertThat(registrations.countConfirmedPairs(e.getId())).isEqualTo(1);
        assertThat(payments.findByTournamentId(e.getId())).hasSize(2);
    }

    @Test void mergingTwoRetainedPlacesReleasesOnePlaceAndPromotesReserveOnce() {
        var o=admin();var e=event(2,o);var a=single(e,RegistrationStatus.CONFIRMED);var b=single(e,RegistrationStatus.CONFIRMED);
        var reserveA=single(e,RegistrationStatus.SOLO_SEARCH);var reserveB=single(e,RegistrationStatus.SOLO_ADD_LATER);
        assertThat(apply(e,o,pairing(reserveA,reserveB)).status()).isEqualTo("WAITLIST");
        var c=checked(e,o,pairing(a,b));
        assertThat(service.apply(e.getId(),o,c).status()).isEqualTo("CONFIRMED");
        service.apply(e.getId(),o,c);
        assertThat(reserveA.getStatus()).isEqualTo(RegistrationStatus.CONFIRMED);
        assertThat(registrations.countConfirmedPairs(e.getId())).isEqualTo(2);
        assertThat(registrations.findByTournamentId(e.getId())).hasSize(4);
    }

    @Test void retainedPlacePlusReserveKeepsMainAndIndividualAttendance() {
        var o=admin();var e=event(1,o);var main=single(e,RegistrationStatus.CONFIRMED);var reserve=single(e,RegistrationStatus.SOLO_SEARCH);
        main.setAttended(true);registrations.saveAndFlush(main);paid(main,"ARS");
        assertThat(apply(e,o,pairing(reserve,main)).status()).isEqualTo("CONFIRMED");
        assertThat(reserve.getMainPlayerId()).isEqualTo(main.getPlayer().getId());
        assertThat(registrations.countConfirmedPairs(e.getId())).isEqualTo(1);
        assertThat(reserve.getAttended()).isFalse();
        assertThat(paymentService.getPaymentManagementData(e.getId())).filteredOn(d->d.getPlayerId().equals(reserve.getPlayer().getId()))
                .singleElement().satisfies(d->assertThat(d.getPaymentStatus()).isNotEqualTo(PaymentStatus.PAID));
    }

    @Test void guestHasNoEmailRequirementAndRetryDoesNotCreateAnotherProfile() {
        var o=admin();var e=event(2,o);long before=players.count();
        var guest=new PlayerInput(null,"Учебный","Гость","+5491176543210",null);
        var c=checked(e,o,new Command(UUID.randomUUID(),Action.ADD,null,null,guest,Kind.ADD_LATER,false,null));
        var r=service.apply(e.getId(),o,c);service.apply(e.getId(),o,c);
        assertThat(players.count()).isEqualTo(before+1);
        var row=registrations.findById(r.registrationId()).orElseThrow();assertThat(row.getPlayer().isGuestAccount()).isTrue();
        var again=apply(e,o,new Command(UUID.randomUUID(),Action.ADD,null,null,new PlayerInput(row.getPlayer().getId(),null,null,null,null),Kind.SEARCH,false,null));
        assertThat(again.existing()).isTrue();assertThat(again.registrationId()).isEqualTo(row.getId());
        assertThat(registrations.findByTournamentId(e.getId())).hasSize(1);
    }

    @Test void multipleGuestsWithoutTelegramCanBeAddedIndependently() {
        var o=admin();var e=event(2,o);
        for(int i=1;i<=2;i++) {
            var guest=new PlayerInput(null,"Гость","Без Telegram "+i,"+549117654322"+i,i==1?null:"");
            var result=apply(e,o,new Command(UUID.randomUUID(),Action.ADD,null,null,guest,Kind.ADD_LATER,false,null));
            assertThat(registrations.findById(result.registrationId()).orElseThrow().getPlayer().getTelegramUsername()).isNull();
        }
        assertThat(registrations.findByTournamentId(e.getId())).hasSize(2);
    }

    @ParameterizedTest
    @CsvSource({
            "+12025550171,+12025550171",
            "+12025550171,+1 (202) 555-0171",
            "+5491176543210,011 15-7654-3210",
            "+79123456789,+7 (912) 345-67-89"
    })
    void guestPhonePreviewAndApplyPreserveEveryExistingProfileField(String storedPhone, String enteredPhone) {
        var owner = admin();
        var event = event(2, owner);
        var existing = player();
        existing.setTelefono(storedPhone);
        existing.setTelegramUsername("original_guest");
        existing.setPlayingSide(PlayingSide.RIGHT);
        players.saveAndFlush(existing);
        var history = single(event(2, owner), RegistrationStatus.SOLO_SEARCH);
        history.setPlayer(existing);
        registrations.saveAndFlush(history);
        var payment = paid(history, "USD");
        String before = profileSnapshot(existing.getId());
        long count = players.count();
        var input = new PlayerInput(null, "Другое", "Имя", enteredPhone, "@incoming_guest", PlayingSide.LEFT);
        var command = new Command(UUID.randomUUID(), Action.ADD, null, null, input, Kind.SEARCH, false, null);

        assertThat(service.preview(event.getId(), owner, command).participants()).singleElement().satisfies(person -> {
            assertThat(person.playerId()).isEqualTo(existing.getId());
            assertThat(person.name()).isEqualTo(existing.getNombreCompleto());
            assertThat(person.playingSide()).isEqualTo(PlayingSide.RIGHT);
        });
        var checked = checked(event, owner, command);
        var result = service.apply(event.getId(), owner, checked);
        assertThat(service.apply(event.getId(), owner, checked)).isEqualTo(result);
        assertThat(apply(event, owner, commandWithNewRequest(command)).existing()).isTrue();
        entities.flush();
        entities.clear();

        assertThat(profileSnapshot(existing.getId())).isEqualTo(before);
        assertThat(players.count()).isEqualTo(count);
        assertThat(registrations.findByTournamentId(event.getId())).singleElement()
                .satisfies(row -> assertThat(row.getPlayer().getId()).isEqualTo(existing.getId()));
        assertThat(payments.findById(payment.getId()).orElseThrow().getRegistration().getId()).isEqualTo(history.getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void addingExistingPlayerAsGuestDoesNotFillAnUnknownSide(boolean guestAccount) {
        var owner = admin();
        var event = event(2, owner);
        var existing = player();
        existing.setTelefono("+12025550172");
        if (guestAccount) existing.setEmail("guest." + UUID.randomUUID() + "@1padel.guest");
        players.saveAndFlush(existing);
        String before = profileSnapshot(existing.getId());
        var input = new PlayerInput(null, "Новый", "Гость", existing.getTelefono(), "new_telegram", PlayingSide.LEFT);

        apply(event, owner, new Command(UUID.randomUUID(), Action.ADD, null, null, input, Kind.ADD_LATER, false, null));
        entities.flush();
        entities.clear();
        assertThat(profileSnapshot(existing.getId())).isEqualTo(before);
    }

    @Test void aNewGuestReceivesSideAndTelegramOnlyAtCreation() {
        var owner = admin();
        var event = event(2, owner);
        var input = new PlayerInput(null, "Новый", "Гость", "+12025550173", "@created_guest", PlayingSide.EITHER);
        var result = apply(event, owner, new Command(UUID.randomUUID(), Action.ADD, null, null, input, Kind.SEARCH, false, null));
        entities.clear();
        var created = registrations.findById(result.registrationId()).orElseThrow().getPlayer();
        assertThat(created.isGuestAccount()).isTrue();
        assertThat(created.getPlayingSide()).isEqualTo(PlayingSide.EITHER);
        assertThat(created.getTelegramUsername()).isEqualTo("created_guest");
        String before = profileSnapshot(created.getId());
        var changed = new PlayerInput(null, "Другое", "Имя", "+1 (202) 555-0173", null, null);
        assertThat(apply(event, owner, new Command(UUID.randomUUID(), Action.ADD, null, null, changed, Kind.SEARCH, false, null)).existing()).isTrue();
        entities.flush();
        assertThat(profileSnapshot(created.getId())).isEqualTo(before);
    }

    @Test void guestPartnerUsesTheSameExistingProfileWithoutOverwritingSide() {
        var owner = admin();
        var event = event(2, owner);
        var source = single(event, RegistrationStatus.SOLO_SEARCH);
        var existing = player();
        existing.setTelefono("+12025550174");
        existing.setPlayingSide(PlayingSide.RIGHT);
        players.saveAndFlush(existing);
        String before = profileSnapshot(existing.getId());
        var input = new PlayerInput(null, "Чужое", "Имя", "+1 (202) 555-0174", "new_partner", PlayingSide.LEFT);
        var result = apply(event, owner, new Command(UUID.randomUUID(), Action.PAIR, source.getId(), null, input, null, false, null));
        entities.flush();
        entities.clear();
        assertThat(registrations.findById(result.registrationId()).orElseThrow().getPartner().getId()).isEqualTo(existing.getId());
        assertThat(profileSnapshot(existing.getId())).isEqualTo(before);
    }

    @Test void conflictingAndAmbiguousGuestContactsAreRejectedBeforeWriting() {
        var owner = admin();
        var event = event(2, owner);
        var first = player();
        first.setTelefono("+12025550175");
        players.saveAndFlush(first);
        var second = player();
        second.setTelefono("+1 (202) 555-0175");
        second.setTelegramUsername("another_profile");
        players.saveAndFlush(second);
        long count = players.count();
        var input = new PlayerInput(null, "Новый", "Гость", first.getTelefono(), null, PlayingSide.LEFT);
        assertThatThrownBy(() -> service.preview(event.getId(), owner,
                new Command(UUID.randomUUID(), Action.ADD, null, null, input, Kind.SEARCH, false, null)))
                .hasMessage("solo.error.contact");
        second.setTelefono("+12025550176");
        players.saveAndFlush(second);
        var conflict = new PlayerInput(null, "Новый", "Гость", first.getTelefono(), "another_profile", PlayingSide.LEFT);
        assertThatThrownBy(() -> service.preview(event.getId(), owner,
                new Command(UUID.randomUUID(), Action.ADD, null, null, conflict, Kind.SEARCH, false, null)))
                .hasMessage("solo.error.contact");
        assertThat(players.count()).isEqualTo(count);
        assertThat(registrations.findByTournamentId(event.getId())).isEmpty();
    }

    @Test void aProfileCreatedAfterGuestPreviewRequiresARecheckWithoutOverwritingIt() {
        var owner = admin();
        var event = event(2, owner);
        var input = new PlayerInput(null, "Новый", "Гость", "+12025550177", null, PlayingSide.LEFT);
        var command = checked(event, owner, new Command(UUID.randomUUID(), Action.ADD, null, null, input, Kind.SEARCH, false, null));
        var existing = player();
        existing.setTelefono(input.phone());
        existing.setPlayingSide(PlayingSide.RIGHT);
        players.saveAndFlush(existing);
        String before = profileSnapshot(existing.getId());
        assertThatThrownBy(() -> service.apply(event.getId(), owner, command)).hasMessage("solo.error.stale");
        entities.flush();
        assertThat(profileSnapshot(existing.getId())).isEqualTo(before);
        assertThat(registrations.findByTournamentId(event.getId())).isEmpty();
    }

    private String profileSnapshot(Long id) {
        return jdbc.queryForObject("SELECT to_jsonb(p)::text FROM player_padel_db p WHERE id=?", String.class, id);
    }

    private Command commandWithNewRequest(Command command) {
        return new Command(UUID.randomUUID(), command.action(), command.registrationId(), command.partnerRegistrationId(),
                command.player(), command.kind(), command.overrideLevel(), null);
    }

    @Test void removalKeepsPaymentsProfileAndHistoryAndRestoringDoesNotReuseOldPayment() {
        var o=admin();var e=event(2,o);var a=single(e,RegistrationStatus.SOLO_SEARCH);var p=paid(a,"USD");
        apply(e,o,new Command(UUID.randomUUID(),Action.REMOVE,a.getId(),null,null,null,false,null));
        assertThat(a.getIsActive()).isFalse();assertThat(players.existsById(a.getPlayer().getId())).isTrue();
        assertThat(payments.findById(p.getId()).orElseThrow().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(service.board(e.getId(),o).cancelled()).singleElement().satisfies(x->assertThat(x.payments()).hasSize(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM registration_cancellation_members WHERE registration_id=?",Long.class,a.getId())).isEqualTo(1);
        var restored=apply(e,o,new Command(UUID.randomUUID(),Action.ADD,null,null,new PlayerInput(a.getPlayer().getId(),null,null,null,null),Kind.SEARCH,false,null));
        assertThat(restored.registrationId()).isEqualTo(a.getId());
        assertThat(service.board(e.getId(),o).singles()).singleElement().satisfies(x->assertThat(x.payments()).isEmpty());
        assertThat(payments.findByTournamentId(e.getId())).hasSize(1);
    }

    @Test void stalePreviewAndChangedIdempotencyPayloadAreRejectedWithoutPartialWrites() {
        var o=admin();var e=event(2,o);var a=single(e,RegistrationStatus.SOLO_SEARCH);var b=single(e,RegistrationStatus.SOLO_SEARCH);
        var c=checked(e,o,pairing(a,b));paid(a,"ARS");
        assertThatThrownBy(()->service.apply(e.getId(),o,c)).isInstanceOf(SoloRosterException.class).hasMessage("solo.error.stale");
        assertThat(a.getPartner()).isNull();
        var fresh=checked(e,o,c);service.apply(e.getId(),o,fresh);
        var conflict=new Command(c.requestId(),Action.REMOVE,a.getId(),null,null,null,false,null);
        assertThatThrownBy(()->service.apply(e.getId(),o,conflict)).hasMessage("solo.error.request");
    }

    @Test void changingEventRequirementsInvalidatesAnEarlierPreview() {
        var o=admin();var e=event(2,o);var a=single(e,RegistrationStatus.SOLO_SEARCH);var b=single(e,RegistrationStatus.SOLO_SEARCH);
        var c=checked(e,o,pairing(a,b));e.setCategoriaNivel(Nivel.C7);events.saveAndFlush(e);
        assertThatThrownBy(()->service.apply(e.getId(),o,c)).hasMessage("solo.error.stale");
        assertThat(a.getPartner()).isNull();
    }

    @ParameterizedTest @EnumSource(value=OwnerRole.class,names={"SUPER_ADMIN","ADMIN","ORGANIZER","CLUB_ADMIN"})
    void everyExistingManagementRoleUsesOrdinaryPageAndApi(OwnerRole role) throws Exception {
        var o=owner(role);var e=event(2,o);var a=single(e,RegistrationStatus.SOLO_SEARCH);
        mvc.perform(get("/admin/tournaments/"+e.getId()+"/participants").with(user(o)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.singles[0].registrationId").value(a.getId()));
        mvc.perform(get("/admin/tournaments/"+e.getId()+"/payments").with(user(o)))
                .andExpect(status().isOk()).andExpect(view().name("admin/tournaments/payments"));
    }

    @Test void ownershipCsrfPlayerRoleAndForeignRegistrationAreEnforcedAtApi() throws Exception {
        var o=owner(OwnerRole.CLUB_ADMIN);var e=event(2,o);var other=owner(OwnerRole.CLUB_ADMIN);other.setClubId(e.getClubId());owners.saveAndFlush(other);
        var a=single(e,RegistrationStatus.SOLO_SEARCH);String url="/admin/tournaments/"+e.getId()+"/participants";
        mvc.perform(get(url).with(user(other))).andExpect(status().isForbidden());
        mvc.perform(get(url).with(user(a.getPlayer()))).andExpect(redirectedUrl("/error/403"));
        var command=new Command(UUID.randomUUID(),Action.REMOVE,a.getId(),null,null,null,false,null);
        mvc.perform(post(url).with(user(o)).contentType("application/json").content(json.writeValueAsString(command))).andExpect(redirectedUrl("/error/403"));
        mvc.perform(post(url).with(user(other)).with(csrf()).contentType("application/json").content(json.writeValueAsString(command))).andExpect(status().isForbidden());
        var foreign=single(event(2,other),RegistrationStatus.SOLO_SEARCH);
        var invalid=new Command(UUID.randomUUID(),Action.PAIR,a.getId(),foreign.getId(),null,null,false,null);
        mvc.perform(post(url+"/preview").with(user(o)).with(csrf()).contentType("application/json").content(json.writeValueAsString(invalid)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("solo.error.single"));
    }

    @ParameterizedTest @ValueSource(booleans={true,false})
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentEmployeesAndConcurrentRetryCannotCreateDuplicatePairs(boolean retry) throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        var ids = tx.execute(status -> {
            var o = admin(); var e = event(1,o);
            var a = single(e,RegistrationStatus.SOLO_SEARCH);var b = single(e,RegistrationStatus.SOLO_ADD_LATER);
            paid(a,"ARS");paid(b,"USD");
            return new long[]{o.getId(),e.getId(),a.getId(),b.getId(),a.getPlayer().getId(),b.getPlayer().getId(),e.getClubId()};
        });
        var command = new Command(UUID.randomUUID(),Action.PAIR,ids[2],ids[3],null,null,false,null);
        var checked = tx.execute(status -> checked(events.findById(ids[1]).orElseThrow(),owners.findById(ids[0]).orElseThrow(),command));
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Result> call = () -> {start.await();return tx.execute(status -> service.apply(ids[1],owners.findById(ids[0]).orElseThrow(),checked));};
            Callable<Result> other = retry ? call : () -> {
                start.await();
                var distinct = new Command(UUID.randomUUID(),checked.action(),checked.registrationId(),checked.partnerRegistrationId(),
                        null,null,false,checked.expectedState());
                return tx.execute(status -> service.apply(ids[1],owners.findById(ids[0]).orElseThrow(),distinct));
            };
            var first = executor.submit(call);var second = executor.submit(other);start.countDown();
            if(retry) assertThat(first.get(30,TimeUnit.SECONDS)).isEqualTo(second.get(30,TimeUnit.SECONDS));
            else {
                int successes=0;
                for(var future:List.of(first,second)) {
                    try {future.get(30,TimeUnit.SECONDS);successes++;}
                    catch(ExecutionException error) {assertThat(error.getCause()).isInstanceOf(SoloRosterException.class);}
                }
                assertThat(successes).isEqualTo(1);
            }
            tx.executeWithoutResult(status -> {
                assertThat(registrations.findByTournamentId(ids[1])).hasSize(2);
                assertThat(registrations.countConfirmedPairs(ids[1])).isEqualTo(1);
                assertThat(payments.findByTournamentId(ids[1])).hasSize(2);
                assertThat(service.board(ids[1],owners.findById(ids[0]).orElseThrow()).history()).hasSize(1);
            });
        } finally {
            executor.shutdownNow();
            tx.executeWithoutResult(status -> {
                var e = events.findById(ids[1]).orElseThrow();e.setIsActive(false);events.saveAndFlush(e);
                jdbc.update("DELETE FROM marketing_payment_progress_db WHERE payment_id IN (SELECT p.id FROM payments_db p JOIN tournament_registrations_db r ON p.registration_id=r.id WHERE r.tournament_id=?)",e.getId());
                jdbc.update("DELETE FROM marketing_paid_slots_db WHERE registration_id IN (SELECT id FROM tournament_registrations_db WHERE tournament_id=?)",e.getId());
                jdbc.update("DELETE FROM marketing_registrations_db WHERE registration_id IN (SELECT id FROM tournament_registrations_db WHERE tournament_id=?)",e.getId());
                tournamentService.deleteTournament(e.getId());
                players.deleteById(ids[4]);players.deleteById(ids[5]);
                owners.deleteById(ids[0]);clubs.deleteById(ids[6]);
            });
        }
    }

    @Test void oldTextGuestHasIndependentAttendanceAndPaymentAfterReload() {
        var o=admin();var e=event(1,o);var a=single(e,RegistrationStatus.CONFIRMED);
        a.setPartnerFirstName("Гость");a.setPartnerLastName("Текстовый");a.setAttended(true);
        registrations.saveAndFlush(a);paid(a,"ARS");
        var guest=new PaymentUpdateDto();guest.setRegistrationId(a.getId());guest.setPartnerRow(true);
        guest.setAttended(false);guest.setAmount(new BigDecimal("25"));guest.setCurrency("USD");
        guest.setPaymentMethod(PaymentMethod.CASH);guest.setPaymentStatus(PaymentStatus.PAID);
        paymentService.savePaymentManagementData(e.getId(),List.of(guest),o.getId());
        entities.flush();entities.clear();
        var data=paymentService.getPaymentManagementData(e.getId());
        assertThat(data).hasSize(2);
        assertThat(data).filteredOn(d->d.isPartnerRow()).singleElement().satisfies(d->{assertThat(d.getAttended()).isFalse();assertThat(d.getCurrency()).isEqualTo("USD");});
        assertThat(data).filteredOn(d->!d.isPartnerRow()).singleElement().satisfies(d->assertThat(d.getAttended()).isTrue());
        guest.setAttended(true);paymentService.savePaymentManagementData(e.getId(),List.of(guest),o.getId());
        entities.flush();entities.clear();
        assertThat(paymentService.getPaymentManagementData(e.getId())).filteredOn(d->d.isPartnerRow())
                .singleElement().satisfies(d->assertThat(d.getAttended()).isTrue());
        assertThat(payments.findByTournamentId(e.getId())).hasSize(2);
    }

    @Test void levelWarningRequiresExplicitManualAdmissionAndProfilesStayUnchanged() {
        var o=admin();var e=event(1,o);e.setCategoriaNivel(Nivel.C7);events.saveAndFlush(e);
        var a=single(e,RegistrationStatus.SOLO_SEARCH);var b=single(e,RegistrationStatus.SOLO_SEARCH);
        b.getPlayer().setNivelJugador(Nivel.SIN_ESPECIFICAR);players.saveAndFlush(b.getPlayer());
        var c=checked(e,o,pairing(a,b));
        assertThat(service.preview(e.getId(),o,c).levelOverrideRequired()).isTrue();
        assertThatThrownBy(()->service.apply(e.getId(),o,c)).hasMessage("solo.error.level");
        var accepted=new Command(c.requestId(),c.action(),c.registrationId(),c.partnerRegistrationId(),c.player(),c.kind(),true,c.expectedState());
        service.apply(e.getId(),o,accepted);
        assertThat(b.getPlayer().getNivelJugador()).isEqualTo(Nivel.SIN_ESPECIFICAR);
    }

    @Test void previouslySavedBracketMatchAlsoProtectsRegistrationComposition() {
        var o=admin();var e=event(2,o);var a=single(e,RegistrationStatus.SOLO_SEARCH);var b=single(e,RegistrationStatus.SOLO_SEARCH);
        brackets.saveAndFlush(Match.builder().tournamentId(e.getId()).ronda(1).partidoNumero(1)
                .tipo(MatchType.PAREJAS).player1Id(a.getPlayer().getId()).player2Id(b.getPlayer().getId()).build());
        assertThatThrownBy(()->service.preview(e.getId(),o,pairing(a,b))).hasMessage("solo.error.matches");
        assertThat(a.getPartner()).isNull();
    }

    @Test void unplayedImportedTeamIsCompletedWithoutDuplicateAndPlayedTeamIsProtected() {
        var o=admin();var e=event(4,o);var a=single(e,RegistrationStatus.CONFIRMED);var b=single(e,RegistrationStatus.SOLO_SEARCH);
        assertThat(conduct.importFromRegistrations(e.getId())).isZero();
        var incomplete=teams.saveAndFlush(AmericanoTeam.builder().tournament(e).teamNumber(1).player1(a.getPlayer()).currentPosition(0).attended(true).build());
        a.setAttended(true);b.setAttended(true);registrations.saveAllAndFlush(List.of(a,b));
        apply(e,o,pairing(a,b));assertThat(teams.findByTournamentId(e.getId())).singleElement().satisfies(t->{assertThat(t.getId()).isEqualTo(incomplete.getId());assertThat(t.getPlayer2()).isNotNull();assertThat(t.getAttended()).isTrue();});
        assertThat(conduct.importFromRegistrations(e.getId())).isZero();
        var c=single(e,RegistrationStatus.CONFIRMED);var d=single(e,RegistrationStatus.SOLO_SEARCH);
        var ct=teams.saveAndFlush(AmericanoTeam.builder().tournament(e).teamNumber(2).player1(c.getPlayer()).currentPosition(0).build());
        var round=rounds.saveAndFlush(AmericanoRound.builder().tournament(e).roundNumber(1).pointsPerMatch(6).isDoubles(true).courts(2).phase(TournamentPhase.QUALIFICATION).build());
        var match=matches.saveAndFlush(AmericanoMatch.builder().tournament(e).round(round).matchNumber(1).isDoubles(true).team1Id(ct.getId()).team2Id(incomplete.getId()).team1Player1(c.getPlayer()).team2Player1(a.getPlayer()).build());
        assertThatThrownBy(()->service.preview(e.getId(),o,pairing(c,d))).hasMessage("solo.error.matches");
        assertThat(matches.findById(match.getId()).orElseThrow().getTeam1Id()).isEqualTo(ct.getId());
        assertThat(c.getPartner()).isNull();
    }
}
