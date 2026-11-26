package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.exception.AccountException;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.telegram.TelegramModels.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockHttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import com.padle.core.padelcoreservice.security.TelegramOnboardingSession;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"app.scheduling.enabled=false","telegram.enabled=false",
        "telegram.players.onboarding-enabled=true"})
@AutoConfigureMockMvc
@Transactional
class PlayerTelegramOnboardingIntegrationTest {
    @MockitoBean PlayerTelegramApi api;
    @MockitoBean PlayerTelegramProperties properties;
    @Autowired PlayerTelegramOnboarding onboarding;
    @Autowired PlayerTelegramOnboardingStore pending;
    @Autowired PlayerTelegramPoller poller;
    @Autowired PlayerTelegramStore connections;
    @Autowired PlayerRepository players;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager transactions;
    private final List<Send> sent=new ArrayList<>();
    private final List<String> deliveryKeys=new ArrayList<>();

    @BeforeEach void setup() {
        when(properties.enabled()).thenReturn(true);
        when(properties.pilotEnabled()).thenReturn(false);
        when(properties.botUsername()).thenReturn("OnePadelQaBot");
        when(api.available()).thenReturn(true);
        when(api.send(anyLong(),anyString(),anyList())).thenAnswer(call -> {
            sent.add(new Send(call.getArgument(0),call.getArgument(1),new Keyboard(call.getArgument(2))));
            return new Result("SENT",(long)sent.size());
        });
    }

    @AfterEach void removeOnlyOwnCommittedDeliveryMarks() {
        sent.stream().flatMap(s -> s.keyboard().rows().stream()).flatMap(List::stream)
                .map(Button::callbackData).filter(data -> data!=null && data.startsWith("l:"))
                .forEach(data -> {
                    deliveryKeys.add("onboarding:"+data.substring(2));
                    deliveryKeys.add("onboarding-complete:"+data.substring(2));
                });
        var cleanup=new TransactionTemplate(transactions);
        cleanup.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        cleanup.executeWithoutResult(status -> deliveryKeys.forEach(key ->
                jdbc.update("DELETE FROM player_telegram_deliveries WHERE dedupe_key=?",key)));
    }

    @Test void twoPlayersConnectOnlyAfterTheirOwnTelegramConfirmation() {
        var first=player(); var second=player();
        String one=start(74101); String two=start(74102);
        assertThat(one).isNotEqualTo(two).matches("[A-Za-z0-9_-]{32}");
        assertThat(onboarding.review(one,first).state()).isEqualTo("READY");
        assertThat(connections.preference(first.getId())).isEqualTo(ContactPreference.UNSPECIFIED);
        assertThat(onboarding.stage(one,first,Locale.forLanguageTag("ru")).state()).isEqualTo("PENDING");
        assertThat(connections.connection(first.getId())).isEmpty();
        UUID id=requestId(one);
        assertThat(onboarding.complete(id,74102)).isEmpty();
        assertThat(onboarding.complete(id,74101)).contains(first.getId());
        var linkedAt=jdbc.queryForObject("SELECT linked_at FROM player_telegram_connections WHERE player_id=?",Object.class,first.getId());
        assertThat(onboarding.complete(id,74101)).contains(first.getId());
        assertThat(jdbc.queryForObject("SELECT linked_at FROM player_telegram_connections WHERE player_id=?",Object.class,first.getId())).isEqualTo(linkedAt);
        onboarding.stage(two,second,Locale.ENGLISH);
        assertThat(onboarding.complete(requestId(two),74102)).contains(second.getId());
        assertThat(connections.preference(first.getId())).isEqualTo(ContactPreference.TELEGRAM);
        assertThat(onboarding.review(one,first).state()).isEqualTo("CONNECTED");
        assertThat(jdbc.queryForObject("SELECT preferred_locale FROM player_padel_db WHERE id=?",String.class,first.getId())).isEqualTo("ru");
    }

    @Test void forwardedLinkCannotChangeCandidateOrConnectWithoutOriginalTelegram() throws Exception {
        var first=player(); var second=player();
        String token=start(74201);
        String json="{\"token\":\""+token+"\",\"playerId\":"+second.getId()+"}";
        mvc.perform(post("/api/players/me/telegram/onboarding").with(user(first))
                .param("request",requestId(token).toString()).sessionAttr(TelegramOnboardingSession.KEY,Map.of(requestId(token).toString(),token)).contentType("application/json").content(json)).andExpect(status().isForbidden());
        mvc.perform(post("/api/players/me/telegram/onboarding").with(user(first)).with(csrf())
                .param("request",requestId(token).toString()).sessionAttr(TelegramOnboardingSession.KEY,Map.of(requestId(token).toString(),token)).contentType("application/json").content(json)).andExpect(status().isOk())
                .andExpect(jsonPath("$.playerName").value(first.getNombreCompleto()))
                .andExpect(jsonPath("$.state").value("PENDING"))
                .andExpect(header().string("Cache-Control","no-store"));
        assertThatThrownBy(() -> onboarding.stage(token,second,Locale.ENGLISH)).isInstanceOf(AccountException.class);
        assertThat(onboarding.complete(requestId(token),74202)).isEmpty();
        assertThat(connections.connection(first.getId())).isEmpty();
        assertThat(connections.connection(second.getId())).isEmpty();
        assertThat(sent.get(sent.size()-1).chatId()).isEqualTo(74201);
    }

    @Test void expiryStopAndWhatsappRevokeOutstandingConfirmation() {
        var player=player();
        String expired=start(74301);
        onboarding.stage(expired,player,Locale.ENGLISH);
        UUID expiredId=requestId(expired);
        jdbc.update("UPDATE player_telegram_onboarding SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE id=?",expiredId);
        assertThatThrownBy(() -> onboarding.review(expired,player)).isInstanceOf(AccountException.class);
        assertThat(onboarding.complete(expiredId,74301)).isEmpty();
        String stopped=start(74302);
        onboarding.stage(stopped,player,Locale.ENGLISH);
        UUID stoppedId=requestId(stopped);
        poller.handle(new Update(uniqueUpdate(),new Message(1,new User(74302),new Chat(74302,"private"),"/stop"),null));
        assertThat(onboarding.complete(stoppedId,74302)).isEmpty();
        String whatsapp=start(74303);
        onboarding.stage(whatsapp,player,Locale.ENGLISH);
        UUID whatsappId=requestId(whatsapp);
        connections.preference(player.getId(),ContactPreference.WHATSAPP);
        assertThat(onboarding.complete(whatsappId,74303)).isEmpty();
        assertThat(connections.preference(player.getId())).isEqualTo(ContactPreference.WHATSAPP);
    }

    @Test void existingConnectionsAreNeverOverwrittenAndOrdinaryLinksStillWork() {
        var first=player(); var second=player();
        connections.token(first.getId(),"legacy-hash",java.time.OffsetDateTime.now().plusMinutes(10),"es");
        assertThat(connections.bind("legacy-hash",74401,74401)).contains(first.getId());
        String other=start(74402);
        assertThatThrownBy(() -> onboarding.stage(other,first,Locale.ENGLISH)).isInstanceOf(AccountException.class);
        String token=start(74403);
        onboarding.stage(token,second,Locale.ENGLISH);
        connections.token(second.getId(),"another-legacy",java.time.OffsetDateTime.now().plusMinutes(10),"es");
        assertThat(connections.bind("another-legacy",74404,74404)).contains(second.getId());
        assertThat(pending.review(PlayerTelegramLinks.hash(token))).isEmpty();
        assertThat(connections.byUser(74401)).map(Connection::playerId).contains(first.getId());
    }

    @Test void disabledPilotInactiveGuestAndGroupCannotConnect() {
        var player=player();
        String token=start(74501);
        onboarding.stage(token,player,Locale.ENGLISH);
        UUID id=requestId(token);
        when(properties.pilotEnabled()).thenReturn(true);
        assertThat(onboarding.invitation().available()).isFalse();
        assertThat(onboarding.complete(id,74501)).isEmpty();
        when(properties.pilotEnabled()).thenReturn(false);
        when(api.available()).thenReturn(false);
        assertThatThrownBy(() -> onboarding.review(token,player)).isInstanceOf(AccountException.class);
        when(api.available()).thenReturn(true);
        jdbc.update("UPDATE player_padel_db SET activo=false WHERE id=?",player.getId());
        assertThatThrownBy(() -> onboarding.complete(id,74501)).isInstanceOf(AccountException.class);
        var guest=players.saveAndFlush(PlayerPadel.builder().nombre("Guest").apellido("QA").email(UUID.randomUUID()+"@1padel.guest")
                .passwordHash("fixture").nivelJugador(Nivel.C7).preferredLocale("es").activo(true).emailConfirmado(true).build());
        assertThatThrownBy(() -> onboarding.stage(start(74502),guest,Locale.ENGLISH)).isInstanceOf(AccountException.class);
        int before=sent.size();
        poller.handle(new Update(uniqueUpdate(),new Message(1,new User(74503),new Chat(-74503,"group"),"/start"),null));
        assertThat(sent).hasSize(before);
    }

    @Test void repeatedStartUpdateAndStageDoNotSendAgain() {
        var player=player();
        long update=uniqueUpdate();
        onboarding.start(update,new User(74601,"ru"));
        String token=lastToken();
        onboarding.start(update,new User(74601,"ru"));
        assertThat(onboarding.review(token,player).state()).isEqualTo("READY");
        assertThat(sent).hasSize(1);
        onboarding.stage(token,player,Locale.ENGLISH);
        onboarding.stage(token,player,Locale.ENGLISH);
        assertThat(sent).hasSize(2);
        assertThat(onboarding.invitation().url()).isEqualTo("https://t.me/OnePadelQaBot?start=connect");
    }

    @Test void secretTravelsInFragmentThenSessionAndLoginDestinationIsClean() throws Exception {
        var player=player();
        String token=start(74701);
        assertThat(sent.get(0).keyboard().rows().get(0).get(0).url()).contains("/telegram/connect#token=").doesNotContain("?token=");
        mvc.perform(post("/telegram/connect/context").contentType("application/json")
                .content("{\"token\":\""+token+"\"}")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/error/403"));
        var context=mvc.perform(post("/telegram/connect/context").with(csrf()).contentType("application/json")
                .content("{\"token\":\""+token+"\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.next").value(TelegramOnboardingSession.destination(requestId(token)))).andReturn();
        var session=(MockHttpSession)context.getRequest().getSession(false);
        mvc.perform(get("/telegram/connect").param("request",requestId(token).toString()).session(session)).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?destination="+URLEncoder.encode(TelegramOnboardingSession.destination(requestId(token)),StandardCharsets.UTF_8)));
        mvc.perform(get("/api/players/me/telegram/onboarding").param("request",requestId(token).toString()).session(session).with(user(player)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("READY"));
        assertThat(connections.connection(player.getId())).isEmpty();
        mvc.perform(post("/login").with(csrf()).param("username","missing-"+UUID.randomUUID()+"@example.test")
                .param("password","fixture").param("destination","/telegram/connect"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error=true&destination=%2Ftelegram%2Fconnect"));
        mvc.perform(post("/api/players/me/telegram/onboarding").with(user(player)).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TELEGRAM_LINK_INVALID"));
    }

    @Test void callbackRequiresOriginalPrivateTelegramAndIsIdempotent() {
        var player=player();
        String token=start(74801);
        onboarding.stage(token,player,Locale.ENGLISH);
        UUID id=requestId(token);
        var foreign=new Callback("foreign",new User(74802),new Message(10,new User(74802),new Chat(74802,"private"),""),"l:"+id);
        poller.handle(new Update(uniqueUpdate(),null,foreign));
        assertThat(connections.connection(player.getId())).isEmpty();
        var own=new Callback("own",new User(74801),new Message(10,new User(74801),new Chat(74801,"private"),""),"l:"+id);
        poller.handle(new Update(uniqueUpdate(),null,own));
        int before=sent.size();
        poller.handle(new Update(uniqueUpdate(),null,own));
        assertThat(connections.byUser(74801)).map(Connection::playerId).contains(player.getId());
        assertThat(sent).hasSize(before);
        verify(api,atLeastOnce()).editKeyboard(74801,10,List.of());
    }

    @Test void requestsInTwoTabsStaySeparateWithinOneSession() throws Exception {
        var first=player(); var second=player();
        String one=start(75001); String two=start(75002);
        var session=new MockHttpSession();
        TelegramOnboardingSession.remember(session,requestId(one),one);
        TelegramOnboardingSession.remember(session,requestId(two),two);
        mvc.perform(post("/api/players/me/telegram/onboarding").param("request",requestId(one).toString())
                .session(session).with(user(first)).with(csrf())).andExpect(status().isOk());
        assertThat(pending.review(PlayerTelegramLinks.hash(one)).orElseThrow().playerId()).isEqualTo(first.getId());
        assertThat(pending.review(PlayerTelegramLinks.hash(two)).orElseThrow().playerId()).isNull();
        mvc.perform(post("/api/players/me/telegram/onboarding").param("request",requestId(two).toString())
                .session(session).with(user(second)).with(csrf())).andExpect(status().isOk());
        assertThat(onboarding.complete(requestId(one),75001)).contains(first.getId());
        assertThat(onboarding.complete(requestId(two),75002)).contains(second.getId());
    }

    @Test
    @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentConfirmationsCannotBindTwoTelegramAccountsToOnePlayer() throws Exception {
        var player=player();
        var executor=Executors.newFixedThreadPool(2);
        try {
            String one=start(74901); String two=start(74902);
            onboarding.stage(one,player,Locale.ENGLISH);
            onboarding.stage(two,player,Locale.ENGLISH);
            UUID first=requestId(one); UUID second=requestId(two);
            var gate=new CountDownLatch(1);
            var a=executor.submit(() -> completeAfter(gate,first,74901));
            var b=executor.submit(() -> completeAfter(gate,second,74902));
            gate.countDown();
            assertThat(a.get(10,TimeUnit.SECONDS) ^ b.get(10,TimeUnit.SECONDS)).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM player_telegram_connections WHERE player_id=?",Long.class,player.getId())).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10,TimeUnit.SECONDS);
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.update("DELETE FROM player_telegram_onboarding WHERE player_id=?",player.getId());
                connections.disconnect(player.getId());
                players.deleteById(player.getId());
            });
        }
    }

    private boolean completeAfter(CountDownLatch gate,UUID id,long userId) throws InterruptedException {
        gate.await();
        try { return onboarding.complete(id,userId).isPresent(); }
        catch (AccountException exception) { return false; }
    }

    private String start(long userId) {
        onboarding.start(uniqueUpdate(),new User(userId,"ru"));
        return lastToken();
    }
    private String lastToken() {
        String url=sent.get(sent.size()-1).keyboard().rows().get(0).get(0).url();
        return url.substring(url.indexOf("token=")+6);
    }
    private UUID requestId(String token) { return pending.review(PlayerTelegramLinks.hash(token)).orElseThrow().id(); }
    private long uniqueUpdate() {
        long update=UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        deliveryKeys.add("reply:"+update);
        return update;
    }
    private PlayerPadel player() {
        return players.saveAndFlush(PlayerPadel.builder().nombre("QA").apellido("Onboarding").email(UUID.randomUUID()+"@gmail.com")
                .passwordHash("fixture").nivelJugador(Nivel.C7).preferredLocale("es").activo(true).emailConfirmado(true).build());
    }
}
