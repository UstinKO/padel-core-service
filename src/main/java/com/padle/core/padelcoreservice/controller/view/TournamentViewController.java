package com.padle.core.padelcoreservice.controller.view;

import com.padle.core.padelcoreservice.dto.KingOfCourtStateDTO;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoPlayerDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoRoundDto;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.TournamentKingOfCourt;
import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import com.padle.core.padelcoreservice.service.KingOfCourtService;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.service.americano.AmericanoService;
import com.padle.core.padelcoreservice.util.SecurityUtils;
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
    private final AmericanoService americanoService; // Добавлен AmericanoService

    @GetMapping("/{id}")
    public String viewTournament(@PathVariable Long id,
                                 Model model,
                                 @AuthenticationPrincipal Object principal) {
        log.info("Viewing tournament details for id: {}", id);

        try {
            PlayerPadel player = SecurityUtils.extractPlayer(principal);

            Optional<TournamentDto> tournamentOpt = tournamentService.getActiveTournamentById(id);

            if (tournamentOpt.isEmpty()) {
                log.warn("Active tournament not found with id: {}", id);
                return "redirect:/?error=tournament_not_found";
            }

            TournamentDto tournament = tournamentOpt.get();

            // ✅ Получаем ВСЕ регистрации на турнир
            List<TournamentRegistrationDto> registrations =
                    tournamentService.getRegistrationsByTournament(id);
            model.addAttribute("registrations", registrations);

            // Логируем для отладки
            for (TournamentRegistrationDto reg : registrations) {
                log.debug("Registration: player={} {}, partner={} {}, status={}, isDouble={}",
                        reg.getPlayerNombre(), reg.getPlayerApellido(),
                        reg.getPartnerNombre(), reg.getPartnerApellido(),
                        reg.getStatus(), reg.getIsDoubleRegistration());
            }

            // ========== БЛОК ДЛЯ AMERICANO ==========
            boolean isAmericanoInitialized = false;
            if (tournament.getTipo() == TournamentType.AMERICANO) {
                isAmericanoInitialized = americanoService.isInitialized(id);
                model.addAttribute("isInitialized", isAmericanoInitialized);

                if (isAmericanoInitialized) {
                    // Получаем количество активных игроков
                    int activePlayers = americanoService.getActivePlayersCount(id);
                    model.addAttribute("activePlayers", activePlayers);

                    // Получаем информацию о лидере
                    List<AmericanoPlayerDto> ranking = americanoService.getRanking(id, "score", "DESC");
                    if (!ranking.isEmpty()) {
                        model.addAttribute("leaderName", ranking.get(0).getPlayerFullName());
                    }

                    // Получаем информацию о раундах
                    List<AmericanoRoundDto> rounds = americanoService.getRounds(id);
                    if (!rounds.isEmpty()) {
                        long completedRounds = rounds.stream()
                                .filter(r -> r.getStatus() == AmericanoRoundStatus.COMPLETED)
                                .count();
                        model.addAttribute("totalRounds", rounds.size());
                        model.addAttribute("completedRounds", completedRounds);
                    }
                } else {
                    // Даже если не инициализирован, добавляем атрибут с дефолтным значением
                    model.addAttribute("activePlayers", 0);
                }
            } else {
                // Для не-Americano турниров добавляем isInitialized = false
                model.addAttribute("isInitialized", false);
            }
            // ========== КОНЕЦ БЛОКА AMERICANO ==========

            // Проверяем наличие King of Court
            List<TournamentKingOfCourt> activeKings = tournamentKingOfCourtRepository
                    .findAllByTournamentIdAndIsActiveTrue(id);

            if (!activeKings.isEmpty()) {
                TournamentKingOfCourt king = activeKings.get(0);
                model.addAttribute("kingOfCourtActive", true);
                KingOfCourtStateDTO kingData = kingOfCourtService.getCurrentState(king.getId());
                model.addAttribute("kingTournament", kingData);
            } else {
                model.addAttribute("kingOfCourtActive", false);
            }

            // Проверяем регистрацию текущего пользователя
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
                    log.info("User registration found: status={}", userRegistration.getStatus());
                } else {
                    log.info("No active registration found for user {} on tournament {}",
                            player.getId(), id);
                }
            }

            int availableSpots = tournament.getDisponibles();

            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            String tournamentJson = mapper.writeValueAsString(tournament);

            model.addAttribute("tournament", tournament);
            model.addAttribute("tournamentJson", tournamentJson);
            model.addAttribute("registrations", registrations);
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