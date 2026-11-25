package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.padelcoreservice.service.TelegramMessagePreviewService;
import com.padle.core.padelcoreservice.telegram.TelegramAudienceModels.*;
import com.padle.core.padelcoreservice.telegram.TelegramModels.ContactPreference;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"telegram.enabled=false", "app.scheduling.enabled=false"})
@AutoConfigureMockMvc
@Transactional
class TelegramMessagePreviewIntegrationTest {
    @Autowired TelegramMessagePreviewService service;
    @Autowired PlayerTelegramStore connections;
    @Autowired PlayerRepository players;
    @Autowired OwnerRepository owners;
    @Autowired TournamentRepository events;
    @Autowired ClubRepository clubs;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired ParticipationRequestRepository requests;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean PlayerTelegramProperties properties;
    @MockitoBean PlayerTelegramApi api;

    @BeforeEach void configure() {
        when(properties.enabled()).thenReturn(true);
        when(properties.pilotEnabled()).thenReturn(false);
    }

    @Test void unionsGroupsAndExactCategoriesWithoutDuplicatesAndHonorsExclusions() {
        var owner = owner(OwnerRole.ORGANIZER);
        var event = event(owner);
        var a = player("Основной", Nivel.C8);
        var b = player("Резерв", Nivel.C8);
        var c = player("Соседний", Nivel.C7);
        row(event, a, RegistrationStatus.CONFIRMED);
        row(event, b, RegistrationStatus.WAITLIST);
        row(event, c, RegistrationStatus.WAITLIST);
        link(a); link(b); link(c);
        var preview = service.preview(owner, selection(event.getId(), Set.of(Group.MAIN), Set.of(Nivel.C8),
                Set.of(a.getId()), Set.of(b.getId())));
        assertThat(preview.recipients()).extracting(Candidate::playerId).containsExactly(a.getId());
        assertThat(preview.selectedCount()).isEqualTo(1);
        assertThat(preview.sendingAvailable()).isFalse();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM player_telegram_deliveries", Long.class)).isZero();
        verifyNoInteractions(api);
    }

    @Test void onlyAnAdministratorCanReadGlobalAudienceAndForeignIdsAreRejected() throws Exception {
        var owner = owner(OwnerRole.ORGANIZER);
        var event = event(owner);
        var foreign = player("Чужой", Nivel.C8);
        assertThatThrownBy(() -> service.audience(owner, null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.preview(owner, selection(event.getId(), Set.of(), Set.of(),
                Set.of(foreign.getId()), Set.of()))).isInstanceOf(AccessDeniedException.class);
        var other = owner(OwnerRole.ORGANIZER);
        mvc.perform(get("/api/admin/telegram/messages/audience?tournamentId=" + event.getId()).with(user(other)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/telegram/messages/audience").with(user(owner))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/telegram/messages/audience").with(user(foreign))).andExpect(status().isForbidden());
        var admin = owner(OwnerRole.SUPER_ADMIN);
        mvc.perform(get("/api/admin/telegram/messages/audience").with(user(admin))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(not(containsString("chatId"))))
                .andExpect(content().string(not(containsString("@example.test"))));
    }

    @Test void previewsRecheckDisconnectAndWhatsappAndDoNotDelegateGuestMessages() {
        var owner = owner(OwnerRole.ORGANIZER);
        var event = event(owner);
        var a = player("Подключён", Nivel.C8);
        var guest = player("Гость", Nivel.C8);
        guest.setEmail("guest." + UUID.randomUUID() + "@1padel.guest"); players.saveAndFlush(guest);
        var row = row(event, a, RegistrationStatus.CONFIRMED); row.setPartner(guest); registrations.saveAndFlush(row);
        link(a);
        var selection = selection(event.getId(), Set.of(Group.ALL), Set.of(), Set.of(), Set.of());
        assertThat(service.preview(owner, selection).recipients()).hasSize(1);
        connections.disconnect(a.getId());
        assertThat(service.preview(owner, selection).unavailable()).extracting(Candidate::availability)
                .containsExactlyInAnyOrder(Availability.NOT_CONNECTED, Availability.GUEST);
        connections.preference(a.getId(), ContactPreference.WHATSAPP);
        assertThat(service.preview(owner, selection).unavailable()).extracting(Candidate::availability)
                .contains(Availability.WHATSAPP);
        verifyNoInteractions(api);
    }

    @Test void soloAndPendingGroupsUseTheActualRegistrationCycleAndCancelledRowsAreExcluded() {
        var owner = owner(OwnerRole.ORGANIZER);
        var event = event(owner);
        var solo = player("Без пары", Nivel.C8);
        var pending = player("Ожидает", Nivel.C8);
        var cancelled = player("Отмена", Nivel.C8);
        row(event, solo, RegistrationStatus.SOLO_ADD_LATER);
        var row = row(event, pending, RegistrationStatus.CONFIRMED);
        requests.saveAndFlush(new ParticipationRequest(row, null, ParticipationRequest.Trigger.ORGANIZER, OffsetDateTime.now()));
        row(event, cancelled, RegistrationStatus.CANCELLED);
        var candidates = service.audience(owner, event.getId()).candidates();
        assertThat(candidates).extracting(Candidate::playerId).doesNotContain(cancelled.getId());
        assertThat(candidates.stream().filter(c -> c.playerId() == solo.getId()).findFirst().orElseThrow().groups())
                .contains(Group.UNPAIRED, Group.RESERVE);
        assertThat(candidates.stream().filter(c -> c.playerId() == pending.getId()).findFirst().orElseThrow().groups())
                .contains(Group.PENDING);
        row.setCancellationVersion(1L); registrations.saveAndFlush(row);
        assertThat(service.audience(owner, event.getId()).candidates().stream()
                .filter(c -> c.playerId() == pending.getId()).findFirst().orElseThrow().groups()).doesNotContain(Group.PENDING);
    }

    @Test void pilotAndFeatureRestrictionsNeverBecomeAvailableRecipients() {
        var admin = owner(OwnerRole.SUPER_ADMIN);
        var event = event(admin);
        var a = player("Пилот", Nivel.C8); link(a); row(event, a, RegistrationStatus.CONFIRMED);
        when(properties.pilotEnabled()).thenReturn(true);
        when(properties.pilotPlayerId()).thenReturn(a.getId());
        when(properties.pilotTournamentId()).thenReturn(event.getId() + 1);
        assertThat(service.audience(admin, event.getId()).candidates()).filteredOn(c -> c.playerId() == a.getId())
                .extracting(Candidate::availability).containsExactly(Availability.OUTSIDE_PILOT);
        when(properties.enabled()).thenReturn(false);
        assertThat(service.audience(admin, event.getId()).candidates()).filteredOn(c -> c.playerId() == a.getId())
                .extracting(Candidate::availability).containsExactly(Availability.DISABLED);
    }

    @Test void previewRequiresCsrfAndValidPayloadAndEmptySelectionDoesNotSelectEveryone() throws Exception {
        var admin = owner(OwnerRole.SUPER_ADMIN);
        player("Не выбирать", Nivel.C8);
        String path = "/api/admin/telegram/messages/preview";
        String body = "{\"groups\":[],\"categories\":[],\"includedPlayerIds\":[],\"excludedPlayerIds\":[],\"text\":\"<b>Привет</b>\"}";
        mvc.perform(post(path).with(user(admin)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.selectedCount").value(0))
                .andExpect(jsonPath("$.text").value("<b>Привет</b>"));
        mvc.perform(post(path).with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post(path).with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("<b>Привет</b>", "x".repeat(4097)))).andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(api);
    }

    private Selection selection(Long id, Set<Group> groups, Set<Nivel> categories, Set<Long> include, Set<Long> exclude) {
        return new Selection(id, groups, categories, include, exclude, "Привет! До встречи.");
    }
    private Owner owner(OwnerRole role) {
        return owners.saveAndFlush(Owner.builder().email(UUID.randomUUID() + "@example.test").password("fixture")
                .firstName("Учебный").lastName("Организатор").role(role).isActive(true).build());
    }
    private PlayerPadel player(String name, Nivel level) {
        return players.saveAndFlush(PlayerPadel.builder().nombre(name).apellido("Тест")
                .email(UUID.randomUUID() + "@example.test").passwordHash("fixture").nivelJugador(level)
                .activo(true).emailConfirmado(true).preferredLocale("ru").build());
    }
    private Tournament event(Owner owner) {
        var club = clubs.saveAndFlush(Club.builder().nombre("Учебный клуб").isActive(true).build());
        return events.saveAndFlush(Tournament.builder().nombre("Учебный турнир").ownerId(owner.getId()).clubId(club.getId())
                .fechaInicio(LocalDate.now().plusDays(2)).horaInicio(LocalTime.NOON).tipo(TournamentType.AMERICANO_TEAMS)
                .modalidad(Modalidad.DOBLES).generoFormato(GenderFormat.MASCULINO).categoriaNivel(Nivel.C8)
                .cupoMax(8).precio(BigDecimal.ZERO).moneda("ARS").estado(TournamentStatus.REGISTRO_ABIERTO)
                .contactoOrganizador("fixture@example.test").build());
    }
    private TournamentRegistration row(Tournament event, PlayerPadel player, RegistrationStatus status) {
        return registrations.saveAndFlush(TournamentRegistration.builder().tournament(event).player(player)
                .status(status).isActive(true).position(1).build());
    }
    private void link(PlayerPadel player) {
        connections.token(player.getId(), UUID.randomUUID().toString(), OffsetDateTime.now().plusMinutes(10), "ru");
        String hash = jdbc.queryForObject("SELECT token_hash FROM player_telegram_tokens WHERE player_id = ?", String.class, player.getId());
        connections.bind(hash, 7_000_000_000L + player.getId(), 7_000_000_000L + player.getId()).orElseThrow();
    }
}
