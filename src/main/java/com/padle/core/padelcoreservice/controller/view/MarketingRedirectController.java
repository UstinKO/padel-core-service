package com.padle.core.padelcoreservice.controller.view;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

// LFPT-0472: короткие маркетинговые ссылки для октябрьского календаря турниров.
// Редирект на уровне приложения (не nginx), чтобы пройти обычный git-flow/деплой.
@Slf4j
@Controller
public class MarketingRedirectController {

    private static final Map<String, String> SHORT_LINK_TARGETS = Map.of(
            "/oct-tg", "https://1-padel.com/?utm_source=telegram&utm_medium=social&utm_campaign=calendar_october",
            "/oct-wa", "https://1-padel.com/?utm_source=whatsapp&utm_medium=message&utm_campaign=calendar_october",
            "/oct-ig", "https://1-padel.com/?utm_source=instagram&utm_medium=social&utm_campaign=calendar_october"
    );

    @GetMapping({"/oct-tg", "/oct-wa", "/oct-ig"})
    public String redirectShortLink(HttpServletRequest request) {
        String target = SHORT_LINK_TARGETS.get(request.getServletPath());
        log.info("Marketing short link redirect: {} -> {}", request.getServletPath(), target);
        return "redirect:" + target;
    }
}
