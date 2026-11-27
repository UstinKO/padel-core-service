package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.repository.OwnerRepository;
import com.padle.core.padelcoreservice.repository.PlayerRepository;
import com.padle.core.padelcoreservice.service.EmailService;
import com.padle.core.padelcoreservice.telegram.TelegramModels.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="browser.qa",matches="true")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "server.address=127.0.0.1","app.scheduling.enabled=false","telegram.enabled=false",
        "telegram.players.onboarding-enabled=true","app.base-url=https://1-padel.com",
        "spring.datasource.url=jdbc:postgresql://${BROWSER_QA_DB_HOST:localhost}:${BROWSER_QA_DB_PORT:5432}/padel_browser_qa",
        "spring.datasource.username=epadel_user","spring.datasource.password=test",
        "app.analytics.meta.enabled=false","app.analytics.ga4-id=","app.analytics.clarity-id=",
        "spring.mail.host=127.0.0.1","spring.mail.username=","spring.mail.password=",
        "disposable-email-domains.checkers.daily-updated-domains=false",
        "disposable-email-domains.checkers.reload.enabled=false"})
class PlayerTelegramJoinBrowserTest {
    @LocalServerPort int port;
    @Autowired PlayerRepository players;
    @Autowired OwnerRepository owners;
    @Autowired PasswordEncoder passwords;
    @Autowired PlayerTelegramPoller poller;
    @Autowired PlayerTelegramStore connections;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean PlayerTelegramApi api;
    @MockitoBean PlayerTelegramProperties properties;
    @MockitoBean EmailService email;
    private final List<Send> sent=new CopyOnWriteArrayList<>();

    record Scenario(String language,int width,String email) {}
    record Handoff(String email,String url) {}
    record Fixture(String baseUrl,String password,String adminEmail,List<Scenario> scenarios) {}

    // Собственный контекст изолирует IP-bucket входа от соседних браузерных сценариев.
    @Test
    @DirtiesContext(methodMode=DirtiesContext.MethodMode.BEFORE_METHOD)
    void siteLinkOpensPersonalBotAndOneStartCompletesConnection() throws Exception {
        when(properties.enabled()).thenReturn(true);
        when(properties.pilotEnabled()).thenReturn(false);
        when(properties.botUsername()).thenReturn("OnePadelQaBot");
        when(api.available()).thenReturn(true);
        when(api.send(anyLong(),anyString(),anyList())).thenAnswer(call -> {
            sent.add(new Send(call.getArgument(0),call.getArgument(1),new Keyboard(call.getArgument(2))));
            return new Result("SENT",(long)sent.size());
        });
        String password="Qa-"+UUID.randomUUID();
        var ownPlayers=new ArrayList<PlayerPadel>();
        var scenarios=new ArrayList<Scenario>();
        var deliveries=new ArrayList<String>();
        Owner owner=new TransactionTemplate(transactions).execute(status -> owners.saveAndFlush(Owner.builder()
                .email(UUID.randomUUID()+"@example.test").password(passwords.encode(password))
                .firstName("QA").lastName("Telegram").role(OwnerRole.SUPER_ADMIN).isActive(true).build()));
        Path folder=Files.createTempDirectory("padel-join-browser-");
        Path input=folder.resolve("private.json");
        Files.createFile(input,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
            int scenarioIndex=0;
            for (String language:List.of("ru","es","en","en")) {
                int index=scenarioIndex++;
                var player=new TransactionTemplate(transactions).execute(status -> players.saveAndFlush(PlayerPadel.builder()
                        .nombre("QA").apellido("Telegram "+index).email(UUID.randomUUID()+"@example.test")
                        .passwordHash(passwords.encode(password)).preferredLocale(language).nivelJugador(Nivel.C7)
                        .activo(true).emailConfirmado(true).build()));
                ownPlayers.add(player);
                scenarios.add(new Scenario(language,List.of(1440,390,320,390).get(index),player.getEmail()));
            }
            var fixture=new Fixture("http://127.0.0.1:"+port,password,owner.getEmail(),scenarios);
            Files.writeString(input,json.writeValueAsString(fixture));
            runBrowser(input,"ready");
            var handoffs=json.readValue(Files.readString(folder.resolve("handoffs.json")),Handoff[].class);
            assertThat(handoffs).hasSize(3);
            for (int i=0;i<handoffs.length;i++) {
                var player=ownPlayers.get(i);
                assertThat(handoffs[i].email()).isEqualTo(player.getEmail());
                assertThat(handoffs[i].url()).matches("https://t.me/OnePadelQaBot\\?start=[A-Za-z0-9_-]{32}");
                String token=handoffs[i].url().split("start=")[1];
                assertThat(connections.connection(player.getId())).isEmpty();
                var user=new User(89100+i);
                long update=UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
                deliveries.add("reply:"+update);
                poller.handle(new Update(update,new Message(1,user,new Chat(user.id(),"private"),"/start "+token),null));
                assertThat(connections.byUser(user.id())).map(Connection::playerId).contains(player.getId());
            }
            assertThat(connections.connection(ownPlayers.get(3).getId())).isEmpty();
            assertThat(sent).hasSize(3);
            runBrowser(input,"connected");
            when(properties.enabled()).thenReturn(false);
            runBrowser(input,"disabled");
            verify(api,never()).updates(anyLong());
        } finally {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                ownPlayers.forEach(player -> { connections.disconnect(player.getId()); players.deleteById(player.getId()); });
                owners.deleteById(owner.getId());
                deliveries.forEach(key -> jdbc.update("DELETE FROM player_telegram_deliveries WHERE dedupe_key=?",key));
            });
            Files.deleteIfExists(folder.resolve("handoffs.json"));
            Files.deleteIfExists(input); Files.deleteIfExists(folder);
        }
    }

    private void runBrowser(Path input,String phase) throws Exception {
        Path output=Path.of("target/telegram-join-qa");
        Files.createDirectories(output);
        var process=new ProcessBuilder("node","tests/browser/telegram-join.browser.mjs",input.toString(),phase)
                .redirectErrorStream(true).redirectOutput(output.resolve(phase+".log").toFile()).start();
        boolean completed=process.waitFor(180,TimeUnit.SECONDS);
        if (!completed) process.destroyForcibly();
        assertThat(completed).as("Браузерная проверка завершилась вовремя").isTrue();
        assertThat(process.exitValue()).as(Files.readString(output.resolve(phase+".log"))).isZero();
    }
}
