package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.persistence.identity.PlayerPadel;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.application.identity.PlayingSide;
import com.padle.core.persistence.identity.PlayerRepository;
import com.padle.core.padelcoreservice.service.EmailService;
import com.padle.core.padelcoreservice.service.RecaptchaService;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static com.padle.core.padelcoreservice.support.PlayerFixtures.playerUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties={"recaptcha.enabled=false", "app.security.whitelisted-ips=127.0.0.1", "app.auth.google-enabled=false"})
@AutoConfigureMockMvc
@Transactional
class PlayingSideUiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PlayerRepository players;
    @MockitoBean EmailService email;
    @MockitoBean RecaptchaService captcha;

    @ParameterizedTest
    @CsvSource({"ru,Справа,С какой стороны ты предпочитаешь играть?", "es,Derecha,¿De qué lado preferís jugar?", "en,Right,Which side do you prefer to play on?"})
    void profileKeepsSavedAnswerAndSurveyHasNoPreselectedAnswer(String language, String side, String title) throws Exception {
        var p = players.save(PlayerPadel.builder().nombre("Fixture").apellido("Player").email(UUID.randomUUID()+"@example.test")
                .passwordHash("unused").nivelJugador(Nivel.SIN_ESPECIFICAR).playingSide(PlayingSide.RIGHT)
                .preferredLocale(language).activo(true).emailConfirmado(true).build());
        var response = mvc.perform(get("/perfil").param("lang",language).with(playerUser(p)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var options = select(response,"playingSide");
        assertThat(Pattern.compile("<option(?=[^>]*value=\"RIGHT\")(?=[^>]*selected)[^>]*>\\s*"+Pattern.quote(side)+"\\s*</option>").matcher(options).find()).isTrue();
        var dialog = response.substring(response.indexOf("id=\"playing-side-dialog\""),response.indexOf("</dialog>"));
        assertThat(dialog).doesNotContain("checked=", "playing-side-description", "<legend").contains(title);
        assertThat(select(response,"playing-side-level")).contains("required", "disabled", "value=\"\"").doesNotContain("SIN_ESPECIFICAR");
        assertThat(select(response,"nivelJugador")).contains("value=\"\"", "disabled", "selected");
        assertThat(response).doesNotContain("??playingSide.");
    }

    @ParameterizedTest
    @CsvSource({"ru,Не указано", "es,Sin indicar", "en,Not specified"})
    void signupAllowsSkippingPreference(String language, String emptyLabel) throws Exception {
        var response = mvc.perform(get("/players/registro").param("lang",language))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var control = select(response,"playingSide");
        assertThat(control).doesNotContain("required").contains(emptyLabel);
        assertThat(Pattern.compile("<option[^>]*value=\"\"[^>]*>").matcher(control).find()).isTrue();
        assertThat(Pattern.compile("<option(?=[^>]*value=\"(?:LEFT|RIGHT|EITHER)\")(?=[^>]*selected)[^>]*>").matcher(control).find()).isFalse();
        assertThat(Pattern.compile("<option\\b").matcher(control).results().count()).isEqualTo(4);
    }
    private String select(String html, String id) {
        var match = Pattern.compile("<select(?=[^>]*id=\""+id+"\")[^>]*>.*?</select>", Pattern.DOTALL).matcher(html);
        assertThat(match.find()).isTrue();
        return match.group();
    }
}
