package com.padle.core.padelcoreservice.controller.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// Веб-версия клиента 1Padel.App — статический экспорт Expo в static/app/.
// Сюда же возвращается вход через Google из приложения (/app/#accessToken=...).
@Controller
public class MobileAppViewController {

    @GetMapping("/app")
    public String redirectToSlash() {
        return "redirect:/app/";
    }

    @GetMapping("/app/")
    public String app() {
        return "forward:/app/index.html";
    }
}
