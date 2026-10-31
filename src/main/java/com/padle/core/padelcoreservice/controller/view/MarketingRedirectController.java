package com.padle.core.padelcoreservice.controller.view;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

// Короткие маркетинговые ссылки (календарь турниров, профиль Instagram).
// Редирект на уровне приложения (не nginx), чтобы проходить обычный git-flow/деплой.
@Slf4j
@Controller
public class MarketingRedirectController {

    private static final Map<String, String> SHORT_LINK_TARGETS = Map.of(
            "/oct-tg", "https://1-padel.com/?utm_source=telegram&utm_medium=social&utm_campaign=calendar_october",
            "/oct-wa", "https://1-padel.com/?utm_source=whatsapp&utm_medium=message&utm_campaign=calendar_october",
            "/oct-ig", "https://1-padel.com/?utm_source=instagram&utm_medium=social&utm_campaign=calendar_october",
            // Короткая ссылка для профиля Instagram ("ссылка в био")
            "/ig-bio", "https://1-padel.com/?utm_source=instagram&utm_medium=social&utm_campaign=instagram_profile",
            "/ig-story", "https://1-padel.com/?utm_source=instagram&utm_medium=social&utm_campaign=instagram_stories&utm_content=shared_story",
            "/ig-ru", "https://1-padel.com/?utm_source=instagram&utm_medium=social&utm_campaign=instagram_profile_ru",
            "/tg-calendar", "https://1-padel.com/?utm_source=telegram&utm_medium=social&utm_campaign=telegram_calendar",
            "/tg-posts", "https://1-padel.com/?utm_source=telegram&utm_medium=social&utm_campaign=telegram_posts",
            "/wa-info", "https://1-padel.com/?utm_source=whatsapp&utm_medium=message&utm_campaign=whatsapp_group",
            "/threads", "https://1-padel.com/?utm_source=threads&utm_medium=social&utm_campaign=threads_profile"
    );

    @GetMapping({"/oct-tg", "/oct-wa", "/oct-ig", "/ig-bio", "/ig-story",
            "/ig-ru", "/tg-calendar", "/tg-posts", "/wa-info", "/threads"})
    public String redirectShortLink(HttpServletRequest request) {
        String target = SHORT_LINK_TARGETS.get(request.getServletPath());
        log.info("Marketing short link redirect: {} -> {}", request.getServletPath(), target);
        return "redirect:" + target;
    }
}
