package com.padle.core.padelcoreservice.controller.view;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Stream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.mail.username=test@example.com",
        "spring.mail.password=test-password",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "recaptcha.site-key=test-site-key",
        "recaptcha.secret-key=test-secret-key"
})
class MarketingRedirectControllerTest {

    private static final String TELEGRAM_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36 "
            + "Telegram-Android/11.2.3";

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0}: анонимный посетитель")
    @MethodSource("shortLinks")
    void anonymousVisitor_reachesPublicHome(String path, String target) throws Exception {
        // MockMvc не вычисляет servletPath автоматически, в отличие от DispatcherServlet.
        mockMvc.perform(get(path).servletPath(path))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(target));
    }

    @ParameterizedTest(name = "{0}: заголовок браузера Telegram")
    @MethodSource("shortLinks")
    void telegramBrowser_reachesPublicHome(String path, String target) throws Exception {
        mockMvc.perform(get(path).servletPath(path).header("User-Agent", TELEGRAM_USER_AGENT))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(target));
    }

    @ParameterizedTest(name = "{0}: авторизованный посетитель")
    @MethodSource("shortLinks")
    void authenticatedVisitor_reachesPublicHome(String path, String target) throws Exception {
        mockMvc.perform(get(path).servletPath(path).with(user("player@test").roles("PLAYER")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(target));
    }

    @ParameterizedTest(name = "{0}: входные параметры не подменяют назначение")
    @MethodSource("shortLinks")
    void externalParameters_doNotChangeDestination(String path, String target) throws Exception {
        mockMvc.perform(get(path).servletPath(path)
                        .param("url", "https://example.com/")
                        .param("redirect", "/login")
                        .param("utm_source", "externo")
                        .param("utm_campaign", "externo"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(target));
    }

    private static Stream<Arguments> shortLinks() {
        return Stream.of(
                Arguments.of("/ig-bio", "https://1-padel.com/?utm_source=instagram&utm_medium=social&utm_campaign=instagram_profile"),
                Arguments.of("/ig-ru", "https://1-padel.com/?utm_source=instagram&utm_medium=social&utm_campaign=instagram_profile_ru"),
                Arguments.of("/tg-calendar", "https://1-padel.com/?utm_source=telegram&utm_medium=social&utm_campaign=telegram_calendar"),
                Arguments.of("/tg-posts", "https://1-padel.com/?utm_source=telegram&utm_medium=social&utm_campaign=telegram_posts"),
                Arguments.of("/wa-info", "https://1-padel.com/?utm_source=whatsapp&utm_medium=message&utm_campaign=whatsapp_group"),
                Arguments.of("/threads", "https://1-padel.com/?utm_source=threads&utm_medium=social&utm_campaign=threads_profile"),
                Arguments.of("/ig-story", "https://1-padel.com/?utm_source=instagram&utm_medium=social&utm_campaign=instagram_stories&utm_content=shared_story"),
                Arguments.of("/oct-tg", "https://1-padel.com/?utm_source=telegram&utm_medium=social&utm_campaign=calendar_october"),
                Arguments.of("/oct-wa", "https://1-padel.com/?utm_source=whatsapp&utm_medium=message&utm_campaign=calendar_october"),
                Arguments.of("/oct-ig", "https://1-padel.com/?utm_source=instagram&utm_medium=social&utm_campaign=calendar_october")
        );
    }
}
