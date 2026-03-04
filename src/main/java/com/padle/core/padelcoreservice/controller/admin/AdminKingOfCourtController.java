package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.dto.KingOfCourtStateDTO;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentKingOfCourt;
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import com.padle.core.padelcoreservice.service.KingOfCourtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/tournaments/king-of-court")
@RequiredArgsConstructor
@Slf4j
public class AdminKingOfCourtController {

    private final KingOfCourtService kingOfCourtService;
    private final TournamentKingOfCourtRepository kingRepository;

    /**
     * Страница управления турниром Король Корта
     */
    @GetMapping("/{kingId}")
    public String viewTournament(@PathVariable Long kingId, Model model) {
        log.info("Viewing King of Court tournament: {}", kingId);

        TournamentKingOfCourt kingTournament = kingRepository.findById(kingId)
                .orElseThrow(() -> new RuntimeException("King tournament not found"));

        // Получаем текущее состояние турнира
        KingOfCourtStateDTO state = kingOfCourtService.getCurrentState(kingId);

        model.addAttribute("kingTournament", kingTournament);
        model.addAttribute("tournament", kingTournament.getTournament());
        model.addAttribute("state", state);
        model.addAttribute("admin", true);

        return "admin/tournaments/king-of-court";
    }

    /**
     * Запуск турнира Король Корта
     */
    @PostMapping("/start")
    public String startTournament(@RequestParam Long tournamentId,
                                  @RequestParam Integer maxCourts,
                                  @RequestParam Integer calibrationRounds,
                                  RedirectAttributes redirectAttributes) {
        try {
            TournamentKingOfCourt kingTournament = kingOfCourtService.initializeTournament(
                    tournamentId, maxCourts, calibrationRounds);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Турнир Король Корта успешно запущен!");

            return "redirect:/admin/tournaments/king-of-court/" + kingTournament.getId();

        } catch (Exception e) {
            log.error("Error starting King of Court tournament", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка запуска турнира: " + e.getMessage());

            return "redirect:/admin/tournaments/" + tournamentId;
        }
    }

    /**
     * Завершение турнира
     */
    @PostMapping("/{kingId}/finish")
    public String finishTournament(@PathVariable Long kingId,
                                   RedirectAttributes redirectAttributes) {
        try {
            TournamentKingOfCourt kingTournament = kingRepository.findById(kingId)
                    .orElseThrow(() -> new RuntimeException("King tournament not found"));

            kingOfCourtService.finishTournament(kingId);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Турнир успешно завершен!");

            return "redirect:/admin/tournaments/" + kingTournament.getTournament().getId();

        } catch (Exception e) {
            log.error("Error finishing tournament", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка завершения турнира: " + e.getMessage());

            return "redirect:/admin/tournaments/king-of-court/" + kingId;
        }
    }

    /**
     * Обновление YouTube ссылки
     */
    @PostMapping("/{kingId}/youtube")
    public String updateYoutubeLink(@PathVariable Long kingId,
                                    @RequestParam String youtubeLink,
                                    RedirectAttributes redirectAttributes) {
        try {
            kingOfCourtService.updateYoutubeLink(kingId, youtubeLink);

            redirectAttributes.addFlashAttribute("successMessage",
                    "YouTube ссылка обновлена");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка обновления ссылки: " + e.getMessage());
        }

        return "redirect:/admin/tournaments/king-of-court/" + kingId;
    }

    /**
     * Переход к следующему раунду
     */
    @PostMapping("/{kingId}/next-round")
    public String nextRound(@PathVariable Long kingId,
                            RedirectAttributes redirectAttributes) {
        try {
            kingOfCourtService.nextRound(kingId);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Переход к следующему раунду выполнен");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка перехода: " + e.getMessage());
        }

        return "redirect:/admin/tournaments/king-of-court/" + kingId;
    }
}