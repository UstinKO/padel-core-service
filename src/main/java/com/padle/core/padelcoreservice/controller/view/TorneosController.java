package com.padle.core.padelcoreservice.controller.view;

import com.padle.core.padelcoreservice.dto.PlayerResponseDto;
import com.padle.core.padelcoreservice.service.PlayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;


@Slf4j
@Controller
@RequestMapping("/torneos")
@RequiredArgsConstructor
public class TorneosController {

    private final PlayerService playerService;

    @GetMapping
    public String verTorneos(Model model) {
        log.info("Accediendo a página de todos los torneos");

        // Получаем информацию о текущем пользователе
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = authentication != null &&
                authentication.isAuthenticated() &&
                !authentication.getName().equals("anonymousUser");

        model.addAttribute("isAuthenticated", isAuthenticated);

        if (isAuthenticated) {
            try {
                String email = authentication.getName();
                PlayerResponseDto player = playerService.obtenerJugadorPorEmail(email);
                model.addAttribute("userName", player.getNombreCompleto());
                model.addAttribute("player", player);
            } catch (Exception e) {
                log.error("Error obteniendo usuario autenticado: {}", e.getMessage());
                model.addAttribute("userName", "Usuario");
            }
        }

        return "torneos";
    }
}