package com.padle.core.padelcoreservice.controller.view;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Collection;
import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final PlayerService playerService;
    private final TournamentService tournamentService;
    private final ObjectMapper objectMapper;

    @GetMapping("/")
    public String homePage(
            // Читаем параметры из URL — надёжнее чем flash-атрибуты
            @RequestParam(value = "registroExitoso", required = false) Boolean registroExitoso,
            @RequestParam(value = "email", required = false) String emailRegistrado,
            Model model) {

        log.info("Accessing home page");

        if (Boolean.TRUE.equals(registroExitoso)) {
            log.info("Post-registro message: email={}", emailRegistrado);
            model.addAttribute("registroExitoso", true);
            model.addAttribute("emailRegistrado", emailRegistrado);
        }

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

        List<TournamentDto> allTournaments = tournamentService.getActiveTournamentsForHome();

        try {
            model.addAttribute("tournamentsJson", objectMapper.writeValueAsString(allTournaments));
        } catch (Exception e) {
            log.error("Error converting tournaments to JSON", e);
            model.addAttribute("tournamentsJson", "[]");
        }

        model.addAttribute("upcomingTournaments", allTournaments);
        model.addAttribute("totalTournaments", allTournaments.size());
        model.addAttribute("totalPlayers", playerService.contarJugadoresActivos());
        model.addAttribute("totalClubs", 50);

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

    @GetMapping("/error/rate-limit")
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public String rateLimitPage(
            @RequestParam(value = "retry", required = false, defaultValue = "60") int retry,
            @RequestParam(value = "blocked", required = false) Boolean blocked,
            Model model) {
        model.addAttribute("retryAfter", retry);
        model.addAttribute("isBlocked", Boolean.TRUE.equals(blocked));
        return "error/rate-limit";
    }

    // #278: дружелюбная страница вместо Whitelabel Error Page — на неё редиректит
    // CustomAccessDeniedHandler для всех 403, кроме отдельно обработанного случая
    // GET /admin/tournaments/{id} у авторизованного не-админа (тот редиректит на
    // публичную страницу турнира напрямую, минуя эту страницу).
    @GetMapping("/error/403")
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String accessDeniedPage(Model model) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = authentication != null &&
                authentication.isAuthenticated() &&
                !authentication.getName().equals("anonymousUser");
        model.addAttribute("isAuthenticated", isAuthenticated);
        return "error/403";
    }

    private boolean isOwner(Authentication authentication) {
        if (authentication == null) return false;
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        return authorities.stream()
                .anyMatch(auth ->
                        auth.getAuthority().equals("ROLE_SUPER_ADMIN") ||
                        auth.getAuthority().equals("ROLE_ORGANIZER") ||
                        auth.getAuthority().equals("ROLE_ADMIN"));
    }
}