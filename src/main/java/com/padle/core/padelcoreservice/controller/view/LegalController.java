package com.padle.core.padelcoreservice.controller.view;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Slf4j
@Controller
public class LegalController {

    @GetMapping("/terminos")
    public String terminos(Model model) {
        log.info("Accediendo a Términos y Condiciones");
        model.addAttribute("year", java.time.Year.now().getValue());
        return "legal/terminos";
    }

    @GetMapping("/privacidad")
    public String privacidad(Model model) {
        log.info("Accediendo a Política de Privacidad");
        model.addAttribute("year", java.time.Year.now().getValue());
        return "legal/privacidad";
    }

    @GetMapping("/cookies")
    public String cookies(Model model) {
        log.info("Accediendo a Política de Cookies");
        model.addAttribute("year", java.time.Year.now().getValue());
        return "legal/cookies";
    }
}