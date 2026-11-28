package com.padle.core.padelcoreservice.telegram;

import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.persistence.identity.PlayerRepository;
import com.padle.core.padelcoreservice.support.OwnerFixtures;
import com.padle.core.padelcoreservice.config.PlayerTelegramProperties;
import com.padle.core.padelcoreservice.config.TelegramCampaignProperties;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import com.padle.core.padelcoreservice.model.*;
import com.padle.core.persistence.tournament.Tournament;
import com.padle.core.persistence.tournament.TournamentRegistration;
import com.padle.core.persistence.clubs.Club;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.application.registration.RegistrationStatus;
import com.padle.core.application.tournament.Modalidad;
import com.padle.core.application.tournament.TournamentStatus;
import com.padle.core.application.tournament.TournamentType;
import com.padle.core.padelcoreservice.repository.*;
import com.padle.core.persistence.tournament.TournamentRegistrationRepository;
import com.padle.core.persistence.tournament.TournamentRepository;
import com.padle.core.persistence.clubs.ClubRepository;
import com.padle.core.padelcoreservice.service.EmailService;
import com.padle.core.padelcoreservice.service.TournamentNotificationService;
import java.math.BigDecimal;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named = "browser.qa", matches = "true")
// Сценарии имеют отдельные счётчики API: история рассылки не расходует лимит проверки языков.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "app.scheduling.enabled=false", "telegram.enabled=false",
        "spring.datasource.url=jdbc:postgresql://${BROWSER_QA_DB_HOST:localhost}:${BROWSER_QA_DB_PORT:5432}/${BROWSER_QA_DB_NAME:padel_browser_qa}",
        "spring.datasource.username=epadel_user", "spring.datasource.password=test",
        "telegram.players.enabled=true", "telegram.players.automatic-enabled=false",
        "app.analytics.meta.enabled=false", "app.analytics.meta.access-token=",
        "app.analytics.ga4-id=", "app.analytics.clarity-id=",
        "disposable-email-domains.checkers.daily-updated-domains=false",
        "disposable-email-domains.checkers.reload.enabled=false",
        "spring.mail.username=", "spring.mail.password=",
        "spring.security.oauth2.client.registration.google.client-id=test",
        "spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test", "recaptcha.secret-key=test"
})
class TelegramMessagePreviewBrowserTest {
    @LocalServerPort int port;
    @Autowired OwnerFixtures owners;
    @Autowired PlayerRepository players;
    @Autowired TournamentRepository events;
    @Autowired TournamentRegistrationRepository registrations;
    @Autowired ClubRepository clubs;
    @Autowired PlayerTelegramStore connections;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @MockitoBean PlayerTelegramProperties properties;
    @MockitoBean TelegramCampaignProperties campaignFlags;
    @Autowired TelegramCampaignWorker campaignWorker;
    @MockitoBean PlayerTelegramApi api;
    @MockitoBean TelegramCampaignTranslator translator;
    @MockitoBean EmailService email;
    @MockitoBean TournamentNotificationService notifications;

    @BeforeEach void configure() {
        when(translator.translate(anyString())).thenAnswer(call -> {
            String source = call.getArgument(0);
            return new TelegramCampaignTranslator.Translation("ru", source, source, source);
        });
        when(campaignFlags.enabled()).thenReturn(false);
        when(properties.enabled()).thenReturn(true);
        when(properties.pilotEnabled()).thenReturn(false);
        when(properties.botUsername()).thenReturn("OnePadelQaBot");
    }
    record Fixture(String baseUrl, String adminEmail, String ownerEmail, String otherEmail,
                   String playerEmail, String password, long eventId, long alphaId, long bravoId, long charlieId) {}

    @Test void realBrowserPreviewsGroupsAndExclusionsOnMobileAndDesktopWithoutSending() throws Exception {
        var fixture = new TransactionTemplate(transactions).execute(status -> seed());
        assertThat(fixture).isNotNull();
        Path directory = Files.createTempDirectory("telegram-preview-browser-");
        Path input = directory.resolve("fixture.json");
        Path output = Path.of("target/telegram-message-preview-qa");
        Files.createDirectories(output);
        Files.writeString(input, json.writeValueAsString(fixture));
        try {
            var process = new ProcessBuilder("node", "tests/browser/telegram-message-preview.browser.mjs", input.toString())
                    .redirectErrorStream(true).redirectOutput(output.resolve("browser.log").toFile()).start();
            boolean completed = process.waitFor(240, TimeUnit.SECONDS);
            if (!completed) process.destroyForcibly();
            assertThat(completed).as("Проверка предпросмотра завершилась вовремя").isTrue();
            assertThat(process.exitValue()).as(Files.readString(output.resolve("browser.log"))).isZero();
            verify(api, never()).send(anyLong(), anyString(), anyList());
            verify(api, never()).updates(anyLong());
        } finally {
            Files.deleteIfExists(input); Files.deleteIfExists(directory);
        }
    }
    @Test void campaignActionsAndHistoryUseOnlyTheMockedTransport() throws Exception {
        var fixture = new TransactionTemplate(transactions).execute(status -> seed());
        assertThat(fixture).isNotNull();
        when(campaignFlags.enabled()).thenReturn(true); when(api.available()).thenReturn(true);
        when(properties.organizerOwnerId()).thenReturn(owners.findByEmail(fixture.adminEmail()).orElseThrow().getId());
        when(properties.organizerChatId()).thenReturn(connections.connection(fixture.alphaId()).orElseThrow().chatId());
        var retries = new AtomicInteger();
        when(api.send(anyLong(),anyString(),anyList())).thenAnswer(invocation -> {
            String text=invocation.getArgument(1);
            return new TelegramModels.Result(text.equals("UNCERTAIN") ? "UNKNOWN"
                    : text.equals("RETRY") && retries.getAndIncrement()==0 ? "FAILED" : "SENT",123L);
        });
        var scheduler=Executors.newSingleThreadScheduledExecutor();
        var failure=new AtomicReference<RuntimeException>();
        scheduler.scheduleWithFixedDelay(()->{
            try { campaignWorker.tick(); } catch(RuntimeException exception) { failure.compareAndSet(null,exception); }
        },0,100,TimeUnit.MILLISECONDS);
        Path directory=Files.createTempDirectory("telegram-campaign-browser-");
        Path input=directory.resolve("fixture.json");
        Path output=Path.of("target/telegram-message-preview-qa"); Files.createDirectories(output);
        Files.writeString(input,json.writeValueAsString(fixture));
        try {
            var process=new ProcessBuilder("node","tests/browser/telegram-campaign.browser.mjs",input.toString())
                    .redirectErrorStream(true).redirectOutput(output.resolve("campaign-browser.log").toFile()).start();
            boolean completed=process.waitFor(240,TimeUnit.SECONDS);
            if(!completed) process.destroyForcibly();
            assertThat(completed).isTrue();
            assertThat(process.exitValue()).as(Files.readString(output.resolve("campaign-browser.log"))).isZero();
            assertThat(failure.get()).isNull();
            verify(api,times(5)).send(eq(7_770_000_000L+fixture.alphaId()),anyString(),anyList());
            verify(api,never()).updates(anyLong());
        } finally {
            scheduler.shutdownNow(); scheduler.awaitTermination(10,TimeUnit.SECONDS);
            Files.deleteIfExists(input); Files.deleteIfExists(directory);
        }
    }
    @Test void expiredSessionPreservesDraftAndNeverRetriesPosts() throws Exception {
        var fixture = new TransactionTemplate(transactions).execute(status -> seed());
        assertThat(fixture).isNotNull();
        when(campaignFlags.enabled()).thenReturn(true); when(api.available()).thenReturn(true);
        when(properties.organizerOwnerId()).thenReturn(owners.findByEmail(fixture.adminEmail()).orElseThrow().getId());
        when(properties.organizerChatId()).thenReturn(connections.connection(fixture.alphaId()).orElseThrow().chatId());
        Path directory = Files.createTempDirectory("telegram-session-browser-");
        Path input = directory.resolve("fixture.json");
        Path output = Path.of("target/telegram-message-preview-qa"); Files.createDirectories(output);
        Files.writeString(input, json.writeValueAsString(fixture));
        try {
            var process = new ProcessBuilder("node", "tests/browser/telegram-session.browser.mjs", input.toString())
                    .redirectErrorStream(true).redirectOutput(output.resolve("session-browser.log").toFile()).start();
            boolean completed = process.waitFor(150, TimeUnit.SECONDS);
            if (!completed) process.destroyForcibly();
            assertThat(completed).isTrue();
            assertThat(process.exitValue()).as(Files.readString(output.resolve("session-browser.log"))).isZero();
            verify(api, never()).send(anyLong(), anyString(), anyList());
            verify(api, never()).updates(anyLong());
        } finally { Files.deleteIfExists(input); Files.deleteIfExists(directory); }
    }
    @Test void localizedCampaignShowsAndSendsTheApprovedProfileLanguage() throws Exception {
        var fixture = new TransactionTemplate(transactions).execute(status -> {
            var seeded = seed();
            var ids = List.of(seeded.alphaId(), seeded.bravoId(), seeded.charlieId());
            var locales = List.of("es", "ru", "en");
            for (int index = 0; index < ids.size(); index++) {
                long id = ids.get(index);
                if (index > 0) {
                    String token = UUID.randomUUID().toString();
                    connections.token(id, token, OffsetDateTime.now().plusMinutes(10), "ru");
                    connections.bind(token, 7_770_000_000L + id, 7_770_000_000L + id).orElseThrow();
                }
                var player = players.findById(id).orElseThrow(); player.setPreferredLocale(locales.get(index));
                players.saveAndFlush(player);
            }
            return seeded;
        });
        when(campaignFlags.enabled()).thenReturn(true); when(api.available()).thenReturn(true);
        when(translator.translate(anyString())).thenAnswer(call -> {
            String source = call.getArgument(0);
            boolean spanish = source.startsWith("Jugamos");
            return new TelegramCampaignTranslator.Translation(spanish ? "es" : "ru",
                    spanish ? source : "Jugamos a las 18:30 — https://1-padel.com",
                    spanish ? "Играем в 18:30 — https://1-padel.com" : source,
                    "We play at 18:30 — https://1-padel.com");
        });
        when(api.send(anyLong(), anyString(), anyList())).thenReturn(new TelegramModels.Result("SENT", 123L));
        var scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleWithFixedDelay(campaignWorker::tick, 0, 100, TimeUnit.MILLISECONDS);
        Path directory = Files.createTempDirectory("telegram-languages-browser-");
        Path input = directory.resolve("fixture.json");
        Path output = Path.of("target/telegram-message-preview-qa"); Files.createDirectories(output);
        Files.createFile(input, PosixFilePermissions.asFileAttribute(
                PosixFilePermissions.fromString("rw-------")));
        Files.writeString(input, json.writeValueAsString(fixture));
        try {
            var process = new ProcessBuilder("node", "tests/browser/telegram-campaign-languages.browser.mjs", input.toString())
                    .redirectErrorStream(true).redirectOutput(output.resolve("languages-browser.log").toFile()).start();
            boolean completed = process.waitFor(180, TimeUnit.SECONDS);
            if (!completed) process.destroyForcibly();
            assertThat(completed).isTrue();
            assertThat(process.exitValue()).as(Files.readString(output.resolve("languages-browser.log"))).isZero();
            verify(api, times(1)).send(7_770_000_000L + fixture.alphaId(), "Nos vemos a las 18:30 — https://1-padel.com", List.of());
            verify(api, times(1)).send(7_770_000_000L + fixture.bravoId(), "Играем в 18:30 — https://1-padel.com", List.of());
            verify(api, times(1)).send(7_770_000_000L + fixture.charlieId(), "We play at 18:30 — https://1-padel.com", List.of());
            verify(api, times(3)).send(anyLong(), anyString(), anyList());
            verify(api, never()).updates(anyLong());
        } finally {
            scheduler.shutdownNow(); scheduler.awaitTermination(10, TimeUnit.SECONDS);
            Files.deleteIfExists(input); Files.deleteIfExists(directory);
        }
    }

    private Fixture seed() {
        String suffix = UUID.randomUUID().toString();
        String password = "Preview-qa-" + suffix;
        var admin = owner(suffix + "admin", password, OwnerRole.SUPER_ADMIN);
        var owner = owner(suffix + "owner", password, OwnerRole.ORGANIZER);
        var other = owner(suffix + "other", password, OwnerRole.ORGANIZER);
        var club = clubs.saveAndFlush(Club.builder().nombre("Учебный клуб " + suffix).isActive(true).build());
        var event = events.saveAndFlush(Tournament.builder().nombre("Telegram preview QA " + suffix)
                .ownerId(owner.getId()).clubId(club.getId()).fechaInicio(LocalDate.now().plusDays(3)).horaInicio(LocalTime.NOON)
                .tipo(TournamentType.AMERICANO_TEAMS).modalidad(Modalidad.DOBLES).generoFormato(GenderFormat.MASCULINO)
                .categoriaNivel(Nivel.C8).cupoMax(8).precio(BigDecimal.ZERO).moneda("ARS")
                .estado(TournamentStatus.REGISTRO_ABIERTO).contactoOrganizador("qa@example.test").build());
        var alpha = player("QA Alpha", suffix, password, Nivel.C8);
        var bravo = player("QA Bravo", suffix, password, Nivel.C8);
        var charlie = player("QA Charlie", suffix, password, Nivel.C7);
        connections.token(alpha.getId(), suffix, OffsetDateTime.now().plusMinutes(10), "ru");
        long chat = 7_770_000_000L + alpha.getId();
        connections.bind(suffix, chat, chat).orElseThrow();
        registrations.saveAndFlush(TournamentRegistration.builder().tournament(event).player(alpha)
                .status(RegistrationStatus.CONFIRMED).isActive(true).position(1).build());
        registrations.saveAndFlush(TournamentRegistration.builder().tournament(event).player(bravo)
                .status(RegistrationStatus.WAITLIST).isActive(true).position(2).build());
        registrations.saveAndFlush(TournamentRegistration.builder().tournament(event).player(charlie)
                .status(RegistrationStatus.SOLO_ADD_LATER).isActive(true).position(3).build());
        return new Fixture("http://127.0.0.1:" + port, admin.getEmail(), owner.getEmail(), other.getEmail(),
                alpha.getEmail(), password, event.getId(), alpha.getId(), bravo.getId(), charlie.getId());
    }
    private Owner owner(String name, String password, OwnerRole role) {
        return owners.saveAndFlush(Owner.builder().email(name + "@example.test").password(passwords.encode(password))
                .firstName("QA").lastName("Messages").role(role).isActive(true).build());
    }
    private PlayerPadel player(String name, String suffix, String password, Nivel level) {
        return players.saveAndFlush(PlayerPadel.builder().nombre(name).apellido("Preview")
                .email(name.replace(" ", "") + suffix + "@example.test").passwordHash(passwords.encode(password))
                .nivelJugador(level).activo(true).emailConfirmado(true).preferredLocale("ru").build());
    }
}
