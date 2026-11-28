package com.padle.core.padelcoreservice.results;

import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.service.EmailService;
import com.padle.core.padelcoreservice.service.TournamentNotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@EnabledIfSystemProperty(named="results.preview",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "server.address=127.0.0.1","server.servlet.session.cookie.name=RESULTS1503SESSION",
        "spring.mail.username=test@example.test","spring.mail.password=test","recaptcha.enabled=false",
        "spring.security.oauth2.client.registration.google.client-id=test","spring.security.oauth2.client.registration.google.client-secret=test",
        "recaptcha.site-key=test","recaptcha.secret-key=test","app.scheduling.enabled=false","telegram.enabled=false",
        "telegram.players.enabled=false","telegram.players.automatic-enabled=false","logging.loki.enabled=false",
        "app.analytics.meta.enabled=false","app.analytics.ga4-id=","app.analytics.clarity-id=","app.auth.google-enabled=false",
        "app.security.whitelisted-ips=127.0.0.1","disposable-email-domains.checkers.daily-updated-domains=false",
        "disposable-email-domains.checkers.reload.enabled=false"
})
class ResultExportPreviewTest {
    @LocalServerPort int port;
    @Autowired ResultExportFixtures fixtures;
    @Autowired PlatformTransactionManager transactions;
    @Autowired ObjectMapper json;
    @MockitoBean EmailService email;
    @MockitoBean TournamentNotificationService notifications;
    record Preview(String baseUrl,String password,String superLogin,String clubLogin,long clubEvent,Map<String,Long> events) {}

    @Test void openIsolatedPreviewUntilExplicitStopFile() throws Exception {
        String password="Results-1503-QA!";
        var preview=new TransactionTemplate(transactions).execute(status->{
            var admin=fixtures.account(OwnerRole.SUPER_ADMIN,password);
            var club=fixtures.account(OwnerRole.CLUB_ADMIN,password);
            var events=List.of("AMERICANO","KING","PLAYOFF","POINTS","SETS","BRACKET","OPEN","CLINIC").stream()
                    .collect(Collectors.toMap(kind->kind,kind->fixtures.sample(kind,admin).event().getId()));
            return new Preview("http://127.0.0.1:"+port,password,admin.getEmail(),club.getEmail(),fixtures.sample("SETS",club).event().getId(),events);
        });
        Files.writeString(Path.of("/private/tmp/results1503-preview.json"),json.writeValueAsString(preview));
        var stop=Path.of("/private/tmp/results1503-preview.stop");
        while(!Files.exists(stop)) Thread.sleep(250);
    }
}
