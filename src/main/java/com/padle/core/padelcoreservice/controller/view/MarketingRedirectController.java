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

    private static final Map<String, String> SHORT_LINK_TARGETS = Map.ofEntries(
            Map.entry("/oct-tg", "https://1-padel.com/?utm_source=telegram&utm_medium=social&utm_campaign=calendar_october"),
            Map.entry("/oct-wa", "https://1-padel.com/?utm_source=whatsapp&utm_medium=message&utm_campaign=calendar_october"),
            Map.entry("/oct-ig", "https://1-padel.com/?utm_source=instagram&utm_medium=social&utm_campaign=calendar_october"),
            // Короткая ссылка для профиля Instagram ("ссылка в био")
            Map.entry("/ig-bio", "https://1-padel.com/?utm_source=instagram&utm_medium=social&utm_campaign=instagram_profile"),
            Map.entry("/ig-story", "https://1-padel.com/?utm_source=instagram&utm_medium=social&utm_campaign=instagram_stories&utm_content=shared_story"),
            Map.entry("/ig-ru", "https://1-padel.com/?utm_source=instagram&utm_medium=social&utm_campaign=instagram_profile_ru"),
            Map.entry("/tg-calendar", "https://1-padel.com/?utm_source=telegram&utm_medium=social&utm_campaign=telegram_calendar"),
            Map.entry("/tg-posts", "https://1-padel.com/?utm_source=telegram&utm_medium=social&utm_campaign=telegram_posts"),
            Map.entry("/wa-info", "https://1-padel.com/?utm_source=whatsapp&utm_medium=message&utm_campaign=whatsapp_group"),
            Map.entry("/wa-broadcast", "https://1-padel.com/?lang=es&utm_source=whatsapp&utm_medium=messaging&utm_campaign=whatsapp_broadcast"),
            Map.entry("/threads", "https://1-padel.com/?utm_source=threads&utm_medium=social&utm_campaign=threads_profile"),
            Map.entry("/vamosplan", "https://1-padel.com/?utm_source=vamosplan&utm_medium=referral&utm_campaign=partner_listing")
    );

    @GetMapping({"/oct-tg", "/oct-wa", "/oct-ig", "/ig-bio", "/ig-story",
            "/ig-ru", "/tg-calendar", "/tg-posts", "/wa-info", "/wa-broadcast", "/threads", "/vamosplan"})
    public String redirectShortLink(HttpServletRequest request) {
        String target = SHORT_LINK_TARGETS.get(request.getServletPath());
        log.info("Marketing short link redirect: {} -> {}", request.getServletPath(), target);
        return "redirect:" + target;
    }
}
