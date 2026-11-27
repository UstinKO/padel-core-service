package com.padle.core.padelcoreservice.telegram;

import com.padle.core.application.tournament.Nivel;
import com.padle.core.application.identity.PlayingSide;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.persistence.identity.PlayerRepository;
import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.service.EmailService;
import com.padle.core.padelcoreservice.support.OwnerFixtures;
import com.padle.core.padelcoreservice.telegram.TelegramModels.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named="browser.qa",matches="true")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "server.address=127.0.0.1","app.security.whitelisted-ips=127.0.0.1,::1",
        "app.scheduling.enabled=false","telegram.enabled=false",
        "telegram.players.enabled=true","telegram.players.onboarding-enabled=true",
        "spring.datasource.url=jdbc:postgresql://${BROWSER_QA_DB_HOST:localhost}:${BROWSER_QA_DB_PORT:5432}/padel_browser_qa",
        "spring.datasource.username=epadel_user","spring.datasource.password=test",
        "app.analytics.meta.enabled=false","app.analytics.ga4-id=","app.analytics.clarity-id=",
        "spring.mail.host=127.0.0.1","spring.mail.username=","spring.mail.password=",
        "disposable-email-domains.checkers.daily-updated-domains=false",
        "disposable-email-domains.checkers.reload.enabled=false"})
class TelegramConnectionsBrowserTest {
    @LocalServerPort int port;
    @Autowired PlayerRepository players;
    @Autowired OwnerFixtures owners;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PlayerTelegramStore connections;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockitoBean PlayerTelegramProperties properties;
    @MockitoBean PlayerTelegramApi api;
    @MockitoBean EmailService email;

    record Scenario(String language, int width, String email, String state) {}
    record Fixture(String baseUrl, String password, String adminEmail, List<Scenario> scenarios,
                   ConnectionStatistics statistics) {}

    @Test void cabinetChoiceAndAdminStatisticsWorkWithoutSending() throws Exception {
        when(properties.enabled()).thenReturn(true);
        when(properties.pilotEnabled()).thenReturn(false);
        when(properties.botUsername()).thenReturn("OnePadelQaBot");
        when(api.available()).thenReturn(true);
        String password="Qa-"+UUID.randomUUID();
        String hash=passwords.encode(password);
        var tx=new TransactionTemplate(transactions);
        var people=new ArrayList<PlayerPadel>();
        var scenarios=new ArrayList<Scenario>();
        var admin=tx.execute(status -> owners.saveAndFlush(Owner.builder().email(UUID.randomUUID()+"@example.test")
                .password(hash).firstName("QA").lastName("Stats").role(OwnerRole.SUPER_ADMIN).isActive(true).build()));
        Path folder=Files.createTempDirectory("padel-connections-browser-");
        Path input=folder.resolve("private.json");
        Files.createFile(input,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        try {
            for (int index=0;index<6;index++) {
                String language=List.of("ru","es","en").get(index%3);
                String state=index<3?"NEW":List.of("CONNECTED","WHATSAPP","DECLINED").get(index-3);
                var player=tx.execute(status -> players.saveAndFlush(PlayerPadel.builder().nombre("QA").apellido("Connections")
                        .email(UUID.randomUUID()+"@example.test").passwordHash(hash).preferredLocale(language)
                        .playingSide(PlayingSide.EITHER)
                        .nivelJugador(Nivel.C7).activo(true).emailConfirmado(true).build()));
                people.add(player);
                tx.executeWithoutResult(status -> {
                    if (state.equals("CONNECTED")) {
                        long id=8_444_000_000L+player.getId();
                        connections.preference(player.getId(),ContactPreference.TELEGRAM);
                        jdbc.update("INSERT INTO player_telegram_connections(player_id,user_id,chat_id,enabled) VALUES (?,?,?,true)",player.getId(),id,id);
                    } else if (state.equals("WHATSAPP")) connections.preference(player.getId(),ContactPreference.WHATSAPP);
                    else if (state.equals("DECLINED")) connections.invitationDismissed(player.getId(),true);
                });
                scenarios.add(new Scenario(language,List.of(1440,390,320).get(index%3),player.getEmail(),state));
            }
            Files.writeString(input,json.writeValueAsString(new Fixture("http://127.0.0.1:"+port,password,admin.getEmail(),scenarios,connections.connectionStatistics())));
            Path output=Path.of("target/telegram-connections-qa");
            Files.createDirectories(output);
            var process=new ProcessBuilder("node","tests/browser/telegram-connections.browser.mjs",input.toString())
                    .redirectErrorStream(true).redirectOutput(output.resolve("browser.log").toFile()).start();
            boolean completed=process.waitFor(240,TimeUnit.SECONDS);
            if (!completed) process.destroyForcibly();
            assertThat(completed).isTrue();
            assertThat(process.exitValue()).as(Files.readString(output.resolve("browser.log"))).isZero();
            verify(api,never()).send(anyLong(),anyString(),anyList());
            verify(api,never()).updates(anyLong());
        } finally {
            tx.executeWithoutResult(status -> {
                people.forEach(player -> { connections.disconnect(player.getId());players.deleteById(player.getId()); });
                owners.deleteById(admin.getId());
            });
            Files.deleteIfExists(input);Files.deleteIfExists(folder);
        }
    }
}
