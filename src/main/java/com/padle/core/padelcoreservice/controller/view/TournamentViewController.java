package com.padle.core.padelcoreservice.controller.view;

import com.padle.core.padelcoreservice.dto.KingOfCourtStateDTO;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.TournamentKingOfCourt;
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import com.padle.core.padelcoreservice.service.KingOfCourtService;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

@Slf4j
@Controller
@RequestMapping("/torneo")
@RequiredArgsConstructor
public class TournamentViewController {

    private final TournamentService tournamentService;
    private final TournamentKingOfCourtRepository tournamentKingOfCourtRepository;
    private final KingOfCourtService kingOfCourtService;

    @GetMapping("/{id}")
    public String viewTournament(@PathVariable Long id,
                                 Model model,
                                 @AuthenticationPrincipal PlayerPadel player) {
        log.info("Viewing tournament details for id: {}", id);

        try {
            // Используем метод, который возвращает только активные турниры
            Optional<TournamentDto> tournamentOpt = tournamentService.getActiveTournamentById(id);

            if (tournamentOpt.isEmpty()) {
                log.warn("Active tournament not found with id: {}", id);
                return "redirect:/?error=tournament_not_found";
            }

            TournamentDto tournament = tournamentOpt.get();

            // Проверяем, есть ли активный турнир "Король Корта"
            List<TournamentKingOfCourt> activeKings = tournamentKingOfCourtRepository
                    .findAllByTournamentIdAndIsActiveTrue(id);

            if (!activeKings.isEmpty()) {
                TournamentKingOfCourt king = activeKings.get(0);
                model.addAttribute("kingOfCourtActive", true);

                // Получаем данные для отображения
                KingOfCourtStateDTO kingData = kingOfCourtService.getCurrentState(king.getId());
                model.addAttribute("kingTournament", kingData);
            } else {
                model.addAttribute("kingOfCourtActive", false);
            }

            // Проверяем, зарегистрирован ли текущий пользователь на этот турнир
            boolean isRegistered = false;
            boolean isInWaitlist = false;
            TournamentRegistrationDto userRegistration = null;

            if (player != null) {
                Optional<TournamentRegistrationDto> registration =
                        tournamentService.getRegistration(id, player.getId());

                if (registration.isPresent()) {
                    isRegistered = true;
                    userRegistration = registration.get();
                    isInWaitlist = userRegistration.getStatus() ==
                            com.padle.core.padelcoreservice.model.enums.RegistrationStatus.WAITLIST;
                    log.info("User registration found: status={}, isActive should be true",
                            userRegistration.getStatus());
                } else {
                    log.info("No active registration found for user {} on tournament {}",
                            player.getId(), id);
                }
            }

            // Получаем количество свободных мест
            int availableSpots = tournament.getDisponibles();

            // Конвертируем турнир в JSON для JavaScript
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            String tournamentJson = mapper.writeValueAsString(tournament);

            model.addAttribute("tournament", tournament);
            model.addAttribute("tournamentJson", tournamentJson);
            model.addAttribute("isRegistered", isRegistered);
            model.addAttribute("isInWaitlist", isInWaitlist);
            model.addAttribute("userRegistration", userRegistration);
            model.addAttribute("availableSpots", availableSpots);
            model.addAttribute("isAuthenticated", player != null);
            model.addAttribute("player", player);

            return "tournament-details";

        } catch (Exception e) {
            log.error("Error viewing tournament: {}", e.getMessage(), e);
            return "redirect:/?error=server_error";
        }
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasRole('OWNER')")
    public String deleteTournament(@PathVariable Long id,
                                   RedirectAttributes redirectAttributes) {
        try {
            tournamentService.deleteTournament(id);
            redirectAttributes.addFlashAttribute("successMessage", "Турнир удален");
            return "redirect:/admin/tournaments";
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/tournaments/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка при удалении: " + e.getMessage());
            return "redirect:/admin/tournaments/" + id;
        }
    }
}