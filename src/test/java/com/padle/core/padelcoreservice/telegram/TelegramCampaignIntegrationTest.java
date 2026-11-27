package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.support.OwnerFixtures;
import com.padle.core.padelcoreservice.config.*;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.persistence.clubs.Club;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.persistence.clubs.ClubRepository;
import com.padle.core.padelcoreservice.telegram.CampaignModels.*;
import com.padle.core.padelcoreservice.telegram.TelegramAudienceModels.*;
import com.padle.core.padelcoreservice.telegram.TelegramModels.Result;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import com.padle.core.padelcoreservice.receipts.ReceiptProperties;
import com.padle.core.padelcoreservice.receipts.ReceiptRegistrations;
import com.padle.core.padelcoreservice.receipts.ReceiptIntake;
import com.padle.core.padelcoreservice.receipts.ReceiptModels;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.scheduling.enabled=false","telegram.enabled=false"})
@AutoConfigureMockMvc
class TelegramCampaignIntegrationTest {
    @Autowired PlayerTelegramLinks personalLinks;
    @Autowired PlayerTelegramScheduler automatic;
    @Autowired ParticipationRequestRepository participationRequests;
    @Autowired PaymentRepository payments;
    @Autowired ReceiptRegistrations receiptChoices;
    @Autowired ReceiptIntake receiptIntake;
    @MockitoBean ReceiptProperties receiptFlags;
    @Autowired TelegramCampaignService campaigns;
    @Autowired TelegramCampaignTranslationService translationService;
    @Autowired TelegramCampaignStore store;
    @Autowired TelegramCampaignWorker worker;
    @Autowired PlayerTelegramStore connections;
    @Autowired PlayerRepository players;
    @Autowired OwnerFixtures owners;
    @Autowired TournamentRepository events;
    @Autowired ClubRepository clubs;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean PlayerTelegramProperties properties;
    @MockitoBean TelegramCampaignProperties flags;
    @MockitoBean PlayerTelegramApi api;
    @MockitoBean TelegramCampaignTranslator translator;

    private final List<Long> fixtureOwners=new ArrayList<>();

    @BeforeEach void configure() {
        when(properties.enabled()).thenReturn(true); when(properties.pilotEnabled()).thenReturn(false);
        when(flags.enabled()).thenReturn(true); when(api.available()).thenReturn(true);
        when(api.send(anyLong(),anyString(),anyList())).thenReturn(new Result("SENT",123L));
    }
    @AfterEach void cleanupCampaigns() {
        if (fixtureOwners.isEmpty()) return;
        String placeholders=String.join(",",Collections.nCopies(fixtureOwners.size(),"?"));
        jdbc.queryForList("SELECT id FROM telegram_campaigns WHERE owner_id IN ("+placeholders+")",UUID.class,fixtureOwners.toArray())
                .forEach(id->{
                    jdbc.update("DELETE FROM player_telegram_deliveries WHERE dedupe_key LIKE ?","campaign:"+id+":%");
                    jdbc.update("DELETE FROM telegram_campaign_actions WHERE campaign_id=?",id);
                    jdbc.update("DELETE FROM telegram_campaign_recipients WHERE campaign_id=?",id);
                    jdbc.update("DELETE FROM telegram_campaigns WHERE id=?",id);
                });
    }
    @Test void prepareFreezesTextAndRecipientsAndConfirmationIsIdempotent() {
        var owner=owner(OwnerRole.ORGANIZER); var event=event(owner); var a=player(); link(a);
        row(event,a); var request=prepare(event.getId(),Set.of(Group.ALL),Set.of());
        var draft=campaigns.prepare(owner,request);
        var b=player(); link(b); row(event,b);
        var repeated=campaigns.prepare(owner,request);
        assertThat(repeated.campaignId()).isEqualTo(draft.campaignId());
        assertThat(repeated.recipients()).extracting(Candidate::playerId).containsExactly(a.getId());
        var changed=new Prepare(request.requestId(),new Selection(event.getId(),Set.of(Group.ALL),Set.of(),Set.of(),Set.of(),"Другой текст"));
        assertThatThrownBy(()->campaigns.prepare(owner,changed)).isInstanceOf(AccountException.class);
        verify(api,never()).send(anyLong(),anyString(),anyList());
        campaigns.confirm(owner,draft.campaignId()); campaigns.confirm(owner,draft.campaignId());
        assertThat(campaigns.prepare(owner,request).sendingAvailable()).isFalse();
        assertThat(campaigns.prepare(owner,request).canTest()).isFalse();
        worker.tick(); worker.tick();
        verify(api,times(1)).send(chat(a),"Тестовое сообщение",List.of());
        assertThat(campaigns.get(owner,draft.campaignId()).state()).isEqualTo("FINISHED");
        assertThat(campaigns.get(owner,draft.campaignId()).text()).isEqualTo("Тестовое сообщение");
    }
    @Test void testGoesOnlyToConfiguredSelfAndNeverQueuesOriginalRecipients() {
        var admin=owner(OwnerRole.SUPER_ADMIN); var self=player(); var target=player(); link(self); link(target);
        when(properties.organizerOwnerId()).thenReturn(admin.getId()); when(properties.organizerChatId()).thenReturn(chat(self));
        var draft=campaigns.prepare(admin,prepare(null,Set.of(),Set.of(target.getId())));
        assertThat(draft.canTest()).isTrue(); var action=new Action(UUID.randomUUID(),true);
        var first=campaigns.test(admin,draft.campaignId(),action);
        assertThat(campaigns.test(admin,draft.campaignId(),action).id()).isEqualTo(first.id());
        worker.tick();
        verify(api,times(1)).send(chat(self),"Тестовое сообщение",List.of());
        campaigns.confirm(admin,draft.campaignId());
        assertThatThrownBy(()->campaigns.test(admin,draft.campaignId(),new Action(UUID.randomUUID(),true)))
                .isInstanceOf(AccountException.class);
        verify(api,never()).send(eq(chat(target)),anyString(),anyList());
        assertThat(campaigns.get(admin,draft.campaignId()).state()).isEqualTo("QUEUED");
        var other=owner(OwnerRole.SUPER_ADMIN);
        assertThatThrownBy(()->campaigns.test(other,draft.campaignId(),new Action(UUID.randomUUID(),true)))
                .isInstanceOf(AccessDeniedException.class);
    }
    @Test void stopAndReconnectDoNotResumeOldQueuedMessages() throws Exception {
        var owner=owner(OwnerRole.ORGANIZER); var event=event(owner); var a=player(); row(event,a); link(a);
        var draft=campaigns.prepare(owner,prepare(event.getId(),Set.of(Group.ALL),Set.of())); campaigns.confirm(owner,draft.campaignId());
        connections.disconnect(a.getId()); link(a); worker.tick();
        assertThat(campaigns.get(owner,draft.campaignId()).recipients()).singleElement().satisfies(r->{
            assertThat(r.state()).isEqualTo("SKIPPED"); assertThat(r.reason()).isEqualTo("CONNECTION_CHANGED");
        });
        verify(api,never()).send(anyLong(),anyString(),anyList());
        var fresh=campaigns.prepare(owner,prepare(event.getId(),Set.of(Group.ALL),Set.of())); campaigns.confirm(owner,fresh.campaignId()); worker.tick();
        verify(api,times(1)).send(chat(a),"Тестовое сообщение",List.of());
    }
    @Test void lossOfPermissionsOrCancellationBeforeDispatchSkipsRecipients() {
        var owner=owner(OwnerRole.ORGANIZER); var event=event(owner); var a=player(); link(a); var row=row(event,a);
        var draft=campaigns.prepare(owner,prepare(event.getId(),Set.of(Group.ALL),Set.of())); campaigns.confirm(owner,draft.campaignId());
        row.cancel("QA cancellation"); registrations.saveAndFlush(row); worker.tick();
        assertThat(campaigns.get(owner,draft.campaignId()).recipients()).singleElement()
                .extracting(Recipient::reason).isEqualTo("REGISTRATION_CHANGED");
        var second=campaigns.prepare(owner,prepare(event.getId(),Set.of(),Set.of())); campaigns.confirm(owner,second.campaignId());
        var admin=owner(OwnerRole.SUPER_ADMIN);
        var global=campaigns.prepare(admin,prepare(null,Set.of(),Set.of(a.getId()))); campaigns.confirm(admin,global.campaignId());
        admin.setRole(OwnerRole.ORGANIZER); owners.saveAndFlush(admin); worker.tick();
        assertThat(store.rows(global.campaignId()).get(0).reason()).isEqualTo("ACCESS_CHANGED");
        verify(api,never()).send(anyLong(),anyString(),anyList());
    }
    @Test void retryOnlyRepeatsCertainFailuresAndUsesAnIdempotentAction() {
        var admin=owner(OwnerRole.SUPER_ADMIN); var a=player();var b=player();var c=player();link(a);link(b);link(c);
        when(api.send(eq(chat(b)),anyString(),anyList())).thenReturn(new Result("FAILED",null),new Result("SENT",456L));
        when(api.send(eq(chat(c)),anyString(),anyList())).thenReturn(new Result("UNKNOWN",null));
        var draft=campaigns.prepare(admin,prepare(null,Set.of(),Set.of(a.getId(),b.getId(),c.getId())));
        campaigns.confirm(admin,draft.campaignId()); worker.tick();
        assertThat(campaigns.get(admin,draft.campaignId()).recipients()).extracting(Recipient::state).containsExactly("SENT","FAILED","UNKNOWN");
        var action=new Action(UUID.randomUUID(),true);
        campaigns.retry(admin,draft.campaignId(),action); campaigns.retry(admin,draft.campaignId(),action); worker.tick();
        verify(api,times(1)).send(chat(a),"Тестовое сообщение",List.of());
        verify(api,times(2)).send(chat(b),"Тестовое сообщение",List.of());
        verify(api,times(1)).send(chat(c),"Тестовое сообщение",List.of());
    }
    @Test void restartRecoversKnownSuccessAndLeavesUncertainAttemptsWithoutRetry() throws Exception {
        var admin=owner(OwnerRole.SUPER_ADMIN);var a=player();var b=player();link(a);link(b);
        var draft=campaigns.prepare(admin,prepare(null,Set.of(),Set.of(a.getId(),b.getId()))); campaigns.confirm(admin,draft.campaignId());
        var pool=Executors.newFixedThreadPool(2);
        try {
            var futures=pool.invokeAll(List.<Callable<Optional<Dispatch>>>of(store::claim,store::claim));
            var one=futures.get(0).get().orElseThrow(); var two=futures.get(1).get().orElseThrow();
            assertThat(one.playerId()).isNotEqualTo(two.playerId());
            connections.claim(one.key(),null,null,8_100_000_000L+one.playerId()); connections.finish(one.key(),new Result("SENT",44L));
            jdbc.update("UPDATE telegram_campaign_recipients SET updated_at=CURRENT_TIMESTAMP-INTERVAL '3 minutes' WHERE campaign_id=?",draft.campaignId());
            store.recover(); worker.tick();
            assertThat(campaigns.get(admin,draft.campaignId()).recipients()).extracting(Recipient::state).containsExactlyInAnyOrder("SENT","UNKNOWN");
            verify(api,never()).send(anyLong(),anyString(),anyList());
        } finally { pool.shutdownNow(); }
    }
    @Test void unavailableFlagExpiredDraftAndForeignDraftCannotSend() {
        var admin=owner(OwnerRole.SUPER_ADMIN);var a=player();link(a);
        var draft=campaigns.prepare(admin,prepare(null,Set.of(),Set.of(a.getId())));
        when(flags.enabled()).thenReturn(false);
        assertThatThrownBy(()->campaigns.confirm(admin,draft.campaignId())).isInstanceOf(AccountException.class);
        worker.tick(); verify(api,never()).send(anyLong(),anyString(),anyList());
        when(flags.enabled()).thenReturn(true);
        jdbc.update("UPDATE telegram_campaigns SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 minute' WHERE id=?",draft.campaignId());
        assertThatThrownBy(()->campaigns.confirm(admin,draft.campaignId())).isInstanceOf(AccountException.class);
        var other=owner(OwnerRole.SUPER_ADMIN);
        assertThatThrownBy(()->campaigns.confirm(other,draft.campaignId())).isInstanceOf(AccessDeniedException.class);
    }
    @Test void apiRequiresCsrfConfirmationAndScopeAndHistoryDoesNotExposeChatOrToken() throws Exception {
        var admin=owner(OwnerRole.SUPER_ADMIN); var a=player(); link(a);
        String path="/api/admin/telegram/messages/campaigns";
        var prepare=prepare(null,Set.of(),Set.of(a.getId()));
        mvc.perform(post(path).with(user(admin)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(prepare)))
                .andExpect(status().isForbidden());
        var draft=campaigns.prepare(admin,prepare);
        mvc.perform(post(path+"/"+draft.campaignId()+"/confirm").with(user(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":false}")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(path).with(user(admin))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(content().string(not(containsString("chatId")))).andExpect(content().string(not(containsString("fingerprint"))));
        var other=owner(OwnerRole.ORGANIZER);
        mvc.perform(get(path+"/"+draft.campaignId()).with(user(other))).andExpect(status().isForbidden());
        verify(api,never()).send(anyLong(),anyString(),anyList());
    }
    @Test void whatsappAndPilotChangesInvalidateQueuedRecipientsAndGlobalPilotCannotSend() {
        var owner=owner(OwnerRole.ORGANIZER);var event=event(owner);var a=player();row(event,a);link(a);
        var first=campaigns.prepare(owner,prepare(event.getId(),Set.of(Group.ALL),Set.of()));campaigns.confirm(owner,first.campaignId());
        connections.preference(a.getId(),TelegramModels.ContactPreference.WHATSAPP);worker.tick();
        assertThat(store.rows(first.campaignId()).get(0).reason()).isEqualTo("WHATSAPP");
        var b=player();row(event,b);link(b);
        var second=campaigns.prepare(owner,prepare(event.getId(),Set.of(Group.ALL),Set.of()));campaigns.confirm(owner,second.campaignId());
        when(properties.pilotEnabled()).thenReturn(true);when(properties.pilotPlayerId()).thenReturn(b.getId());
        when(properties.pilotTournamentId()).thenReturn(event.getId()+999);worker.tick();
        assertThat(store.rows(second.campaignId()).stream().filter(r->r.playerId()==b.getId()).findFirst().orElseThrow().reason())
                .isEqualTo("OUTSIDE_PILOT");
        var admin=owner(OwnerRole.SUPER_ADMIN);var global=campaigns.prepare(admin,prepare(null,Set.of(),Set.of(b.getId())));
        assertThat(global.sendingAvailable()).isFalse();
        assertThatThrownBy(()->campaigns.confirm(admin,global.campaignId())).isInstanceOf(AccountException.class);
        verify(api,never()).send(anyLong(),anyString(),anyList());
    }
    @Test void generalLinkingAndManualCampaignKeepReceiptsInTheirIndependentPilot() throws Exception {
        var owner = owner(OwnerRole.ORGANIZER);
        var event = event(owner);
        var allowed = player();
        var outsideReceiptPilot = player();
        var allowedRow = row(event, allowed);
        var otherRow = row(event, outsideReceiptPilot);
        when(properties.botUsername()).thenReturn("OnePadelQaBot");
        connectThroughPersonalLink(allowed);
        connectThroughPersonalLink(outsideReceiptPilot);
        when(receiptFlags.enabled()).thenReturn(true);
        when(receiptFlags.pilotEnabled()).thenReturn(true);
        when(receiptFlags.pilotPlayerId()).thenReturn(allowed.getId());
        when(receiptFlags.pilotTournamentId()).thenReturn(event.getId());
        when(properties.automaticEnabled()).thenReturn(false);
        when(properties.dailySummaryEnabled()).thenReturn(false);
        long requestsBefore = participationRequests.count();
        automatic.tick();
        automatic.daily();
        var draft = campaigns.prepare(owner, prepare(event.getId(), Set.of(Group.ALL), Set.of()));
        assertThat(draft.recipients()).extracting(Candidate::playerId)
                .containsExactlyInAnyOrder(allowed.getId(), outsideReceiptPilot.getId());
        worker.tick();
        verify(api, never()).send(anyLong(), anyString(), anyList());
        campaigns.confirm(owner, draft.campaignId());
        worker.tick();
        verify(api).send(chat(allowed), "Тестовое сообщение", List.of());
        verify(api).send(chat(outsideReceiptPilot), "Тестовое сообщение", List.of());
        assertReceiptPilotStillClosed(allowedRow, otherRow);
        mvc.perform(delete("/api/players/me/telegram").with(user(outsideReceiptPilot)).with(csrf()))
                .andExpect(status().isNoContent());
        var afterStop = campaigns.prepare(owner, prepare(event.getId(), Set.of(Group.ALL), Set.of()));
        assertThat(afterStop.recipients()).extracting(Candidate::playerId).containsExactly(allowed.getId());
        assertThat(participationRequests.count()).isEqualTo(requestsBefore);
        worker.tick();
        verify(api, times(2)).send(anyLong(), anyString(), anyList());
    }

    private void connectThroughPersonalLink(PlayerPadel player) throws Exception {
        var response = mvc.perform(post("/api/players/me/telegram/link").with(user(player)).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse();
        var link = json.readValue(response.getContentAsString(), TelegramModels.Link.class);
        String token = link.url().substring(link.url().indexOf("?start=") + 7);
        assertThat(personalLinks.bind(token, chat(player), chat(player))).contains(player.getId());
        mvc.perform(get("/api/players/me/telegram").with(user(player)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.connected").value(true));
    }

    private void assertReceiptPilotStillClosed(TournamentRegistration allowed, TournamentRegistration other) {
        assertThat(receiptChoices.choices(allowed.getPlayer().getId()))
                .extracting(TournamentRegistration::getId).containsExactly(allowed.getId());
        assertThat(receiptChoices.choices(other.getPlayer().getId())).isEmpty();
        assertThatThrownBy(() -> receiptIntake.receive(9_000_000 + other.getId(), other.getPlayer().getId(),
                chat(other.getPlayer()), "fixture-file")).isInstanceOf(AccountException.class);
        var upload = receiptIntake.receive(9_000_000 + allowed.getId(), allowed.getPlayer().getId(),
                chat(allowed.getPlayer()), "fixture-file");
        var file = new ReceiptModels.File("application/pdf", "%PDF-1.7\nsynthetic receipt".getBytes(StandardCharsets.US_ASCII));
        var receipt = receiptIntake.attach(upload.id(), allowed.getPlayer().getId(), chat(allowed.getPlayer()),
                allowed.getId(), allowed.getCancellationVersion(), file);
        assertThat(receipt.state()).isEqualTo(ReceiptModels.State.PENDING);
        assertThat(payments.findByRegistrationId(allowed.getId())).isEmpty();
    }

    @Test void localizedDraftFreezesEachProfileLanguageAndExactText() {
        var admin = owner(OwnerRole.SUPER_ADMIN);
        var es = localizedPlayer("es"); var ru = localizedPlayer("ru");
        var en = localizedPlayer("en"); var fallback = localizedPlayer("de");
        var texts = new Texts("Hola 👋 18:30 https://1-padel.com", "Привет 👋 18:30 https://1-padel.com", "Hello 👋 18:30 https://1-padel.com");
        var selection = new Selection(null, Set.of(), Set.of(), Set.of(es.getId(), ru.getId(), en.getId(), fallback.getId()), Set.of(), "Hola");
        var request = new Prepare(UUID.randomUUID(), selection, texts);
        var draft = campaigns.prepare(admin, request);
        assertThat(draft.texts()).isEqualTo(texts);
        assertThat(draft.recipients()).extracting(Candidate::language).containsExactlyInAnyOrder("es", "ru", "en", "es");
        assertThatThrownBy(() -> campaigns.prepare(admin, new Prepare(request.requestId(), selection,
                new Texts(texts.es(), texts.ru(), "Changed")))).isInstanceOf(AccountException.class);
        en.setPreferredLocale("ru"); players.saveAndFlush(en);
        campaigns.confirm(admin, draft.campaignId()); worker.tick();
        verify(api).send(chat(es), texts.es(), List.of());
        verify(api).send(chat(ru), texts.ru(), List.of());
        verify(api).send(chat(en), texts.en(), List.of());
        verify(api).send(chat(fallback), texts.es(), List.of());
        assertThat(campaigns.get(admin, draft.campaignId()).recipients()).filteredOn(r -> r.playerId() == en.getId())
                .singleElement().satisfies(r -> { assertThat(r.language()).isEqualTo("en"); assertThat(r.text()).isEqualTo(texts.en()); });
    }

    @Test void missingTranslationCannotCreateDraftAndOversizedVersionsFailValidation() throws Exception {
        var admin = owner(OwnerRole.SUPER_ADMIN); var target = localizedPlayer("en");
        var selection = prepare(null, Set.of(), Set.of(target.getId())).selection();
        var id = UUID.randomUUID();
        assertThatThrownBy(() -> campaigns.prepare(admin, new Prepare(id, selection, new Texts("Hola", "Привет", ""))))
                .isInstanceOf(AccountException.class).hasMessage("TELEGRAM_TRANSLATION_REQUIRED");
        assertThat(store.byRequest(admin.getId(), id)).isEmpty();
        assertThatThrownBy(() -> campaigns.prepare(admin, new Prepare(id, selection, Texts.empty())))
                .isInstanceOf(AccountException.class).hasMessage("TELEGRAM_TRANSLATION_REQUIRED");
        mvc.perform(post("/api/admin/telegram/messages/campaigns").with(user(admin)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(new Prepare(id, selection,
                        new Texts("Hola", "Привет", "x".repeat(4097)))))).andExpect(status().isUnprocessableEntity());
        verify(api, never()).send(anyLong(), anyString(), anyList());
    }

    @Test void localizedTestAndFailedRetryUseTheSavedVersion() {
        var admin = owner(OwnerRole.SUPER_ADMIN); var self = localizedPlayer("es"); var target = localizedPlayer("en");
        when(properties.organizerOwnerId()).thenReturn(admin.getId()); when(properties.organizerChatId()).thenReturn(chat(self));
        var texts = new Texts("Español", "Русский", "English");
        var draft = campaigns.prepare(admin, new Prepare(UUID.randomUUID(), prepare(null, Set.of(), Set.of(target.getId())).selection(), texts));
        campaigns.test(admin, draft.campaignId(), new Action(UUID.randomUUID(), true)); worker.tick();
        verify(api).send(chat(self), "Español", List.of());
        when(api.send(eq(chat(target)), anyString(), anyList())).thenReturn(new Result("FAILED", null), new Result("SENT", 123L));
        campaigns.confirm(admin, draft.campaignId()); worker.tick();
        target.setPreferredLocale("ru"); players.saveAndFlush(target);
        campaigns.retry(admin, draft.campaignId(), new Action(UUID.randomUUID(), true)); worker.tick();
        verify(api, times(2)).send(chat(target), "English", List.of());
        var legacy = campaigns.prepare(admin, prepare(null, Set.of(), Set.of(target.getId())));
        jdbc.update("UPDATE telegram_campaign_recipients SET message_locale=NULL,message_text=NULL WHERE campaign_id=?", legacy.campaignId());
        campaigns.confirm(admin, legacy.campaignId()); worker.tick();
        verify(api).send(chat(target), "Тестовое сообщение", List.of());
        assertThat(campaigns.get(admin, legacy.campaignId()).recipients()).singleElement()
                .satisfies(r -> { assertThat(r.language()).isEmpty(); assertThat(r.text()).isEqualTo("Тестовое сообщение"); });
    }

    @Test void translationEndpointChecksRightsCsrfAndDoesNotCreateCampaignOrSend() throws Exception {
        var admin = owner(OwnerRole.SUPER_ADMIN); var target = localizedPlayer("es");
        var selection = prepare(null, Set.of(), Set.of(target.getId())).selection();
        when(translator.translate(selection.text())).thenReturn(new TelegramCampaignTranslator.Translation("ru", "Hola", selection.text(), "Hello"));
        var none = translationService.translate(admin, prepare(null, Set.of(), Set.of()).selection());
        assertThat(none.texts().localized()).isFalse();
        assertThat(none.languages()).isEmpty();
        String path = "/api/admin/telegram/messages/translations";
        mvc.perform(post(path).with(user(admin)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(selection)))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).with(user(admin)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(selection)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.texts.es").value("Hola")).andExpect(jsonPath("$.languages[0].language").value("es"));
        var foreign = owner(OwnerRole.ORGANIZER);
        mvc.perform(post(path).with(user(foreign)).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(selection)))
                .andExpect(status().isForbidden());
        verify(translator, times(1)).translate(selection.text());
        assertThat(store.history(null, 0)).noneMatch(c -> c.ownerId() == admin.getId());
        verify(api, never()).send(anyLong(), anyString(), anyList());
    }

    @Test void translationLimitAndMissingSnapshotFailClosed() {
        var admin = owner(OwnerRole.SUPER_ADMIN); var target = localizedPlayer("en");
        var selection = prepare(null, Set.of(), Set.of(target.getId())).selection();
        when(translator.translate(selection.text())).thenReturn(new TelegramCampaignTranslator.Translation("ru", "Hola", selection.text(), "Hello"));
        for (int index = 0; index < 6; index++) { translationService.translate(admin, selection); }
        assertThatThrownBy(() -> translationService.translate(admin, selection)).isInstanceOf(AccountException.class)
                .hasMessage("TELEGRAM_TRANSLATION_LIMIT");
        verify(translator, times(6)).translate(selection.text());
        var draft = campaigns.prepare(admin, new Prepare(UUID.randomUUID(), selection, new Texts("Hola", "Привет", "Hello")));
        campaigns.confirm(admin, draft.campaignId());
        jdbc.update("UPDATE telegram_campaign_recipients SET message_text=NULL WHERE campaign_id=?", draft.campaignId());
        worker.tick();
        assertThat(campaigns.get(admin, draft.campaignId()).recipients()).singleElement()
                .satisfies(r -> { assertThat(r.state()).isEqualTo("SKIPPED"); assertThat(r.reason()).isEqualTo("TRANSLATION_MISSING"); });
        verify(api, never()).send(anyLong(), anyString(), anyList());
    }

    private PlayerPadel localizedPlayer(String language) {
        var player = player(); link(player); player.setPreferredLocale(language);
        return players.saveAndFlush(player);
    }

    private Prepare prepare(Long event,Set<Group> groups,Set<Long> ids) {
        return new Prepare(UUID.randomUUID(),new Selection(event,groups,Set.of(),ids,Set.of(),"Тестовое сообщение"));
    }
    private Owner owner(OwnerRole role) {
        var owner=owners.saveAndFlush(Owner.builder().email(UUID.randomUUID()+"@example.test").password("fixture")
                .firstName("QA").lastName("Sender").role(role).isActive(true).build());
        fixtureOwners.add(owner.getId());return owner;
    }
    private PlayerPadel player() {
        long n=players.count()+1;
        return players.saveAndFlush(PlayerPadel.builder().nombre(String.format("QA %08d",n)).apellido("Campaign")
                .email(UUID.randomUUID()+"@example.test").passwordHash("fixture").nivelJugador(Nivel.C8)
                .activo(true).emailConfirmado(true).preferredLocale("ru").build());
    }
    private Tournament event(Owner owner) {
        var club=clubs.saveAndFlush(Club.builder().nombre("QA Campaign "+UUID.randomUUID()).isActive(true).build());
        return events.saveAndFlush(Tournament.builder().nombre("QA Campaign").ownerId(owner.getId()).clubId(club.getId())
                .fechaInicio(LocalDate.now().plusDays(2)).horaInicio(LocalTime.NOON).tipo(TournamentType.AMERICANO_TEAMS)
                .modalidad(Modalidad.DOBLES).generoFormato(GenderFormat.MASCULINO).categoriaNivel(Nivel.C8).cupoMax(8)
                .precio(BigDecimal.ZERO).moneda("ARS").estado(TournamentStatus.REGISTRO_ABIERTO).contactoOrganizador("qa@example.test").build());
    }
    private TournamentRegistration row(Tournament event,PlayerPadel player) {
        return registrations.saveAndFlush(TournamentRegistration.builder().tournament(event).player(player).status(RegistrationStatus.CONFIRMED)
                .isActive(true).position(1).build());
    }
    private long chat(PlayerPadel player) { return 8_100_000_000L+player.getId(); }
    private void link(PlayerPadel player) {
        String hash=UUID.randomUUID().toString();connections.token(player.getId(),hash,OffsetDateTime.now().plusMinutes(10),"ru");
        connections.bind(hash,chat(player),chat(player)).orElseThrow();
    }
}
