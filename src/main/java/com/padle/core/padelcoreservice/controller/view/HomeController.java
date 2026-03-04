package com.padle.core.padelcoreservice.controller.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.padle.core.padelcoreservice.dto.PlayerResponseDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.service.PlayerService;
import com.padle.core.padelcoreservice.service.TournamentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Collection;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final PlayerService playerService;
    private final TournamentService tournamentService;

    @GetMapping("/")
    public String homePage(Model model) {
        log.info("Accessing home page");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = authentication != null &&
                authentication.isAuthenticated() &&
                !authentication.getName().equals("anonymousUser");

        model.addAttribute("isAuthenticated", isAuthenticated);

        if (isAuthenticated) {
            try {
                String email = authentication.getName();

                if (isOwner(authentication)) {
                    model.addAttribute("userName", "Admin");
                    model.addAttribute("isOwner", true);
                } else {
                    PlayerResponseDto player = playerService.obtenerJugadorPorEmail(email);
                    model.addAttribute("userName", player.getNombreCompleto());
                    model.addAttribute("isOwner", false);
                }
            } catch (Exception e) {
                log.error("Error obteniendo usuario autenticado: {}", e.getMessage());
                model.addAttribute("userName", "Usuario");
                model.addAttribute("isOwner", false);
            }
        }

        // Получаем активные турниры со статусом REGISTRO_ABIERTO или PUBLICADO
        List<TournamentDto> allTournaments = tournamentService.getActiveTournamentsForHome();

        // Конвертируем в JSON для JavaScript фильтров
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            String tournamentsJson = mapper.writeValueAsString(allTournaments);
            model.addAttribute("tournamentsJson", tournamentsJson);
        } catch (Exception e) {
            log.error("Error converting tournaments to JSON", e);
            model.addAttribute("tournamentsJson", "[]");
        }

        model.addAttribute("upcomingTournaments", allTournaments);
        model.addAttribute("totalTournaments", allTournaments.size());
        model.addAttribute("totalPlayers", playerService.contarJugadoresActivos());
        model.addAttribute("totalClubs", 50); // TODO: Получить реальное количество клубов

        return "index";
    }

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            @RequestParam(value = "registered", required = false) String registered,
            Model model) {

        log.info("Accediendo a página de login");

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = authentication != null &&
                authentication.isAuthenticated() &&
                !authentication.getName().equals("anonymousUser");

        if (isAuthenticated) {
            if (isOwner(authentication)) {
                return "redirect:/admin";
            } else {
                return "redirect:/players/dashboard";
            }
        }

        if (error != null) {
            model.addAttribute("error", true);
        }
        if (logout != null) {
            model.addAttribute("logout", true);
        }
        if (registered != null) {
            model.addAttribute("registered", true);
        }

        return "login";
    }

    private boolean isOwner(Authentication authentication) {
        if (authentication == null) return false;

        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        return authorities.stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_OWNER"));
    }
}