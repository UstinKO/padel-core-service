package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.dto.MatchDto;
import com.padle.core.padelcoreservice.dto.PlayerResponseDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.TournamentKingOfCourt;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import com.padle.core.padelcoreservice.service.ClubService;
import com.padle.core.padelcoreservice.service.MatchService;
import com.padle.core.padelcoreservice.service.OwnerService;
import com.padle.core.padelcoreservice.service.PlayerService;
import com.padle.core.padelcoreservice.service.TournamentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final TournamentService tournamentService;
    private final ClubService clubService;
    private final PlayerService playerService;
    private final OwnerService ownerService;
    private final MatchService matchService;
    private final TournamentKingOfCourtRepository tournamentKingOfCourtRepository;

    @GetMapping
    public String adminPanel(Model model, @AuthenticationPrincipal Owner owner) {
        log.info("Accessing admin panel for owner: {}", owner.getEmail());

        // Obtener estadísticas
        long totalPlayers = playerService.contarJugadoresActivos(); // Вместо getTotalActivePlayers()
        long totalOwners = ownerService.getTotalActiveOwners();
        long totalTournaments = tournamentService.getTotalActiveTournaments();
        long totalWaitlist = tournamentService.getTotalWaitlistCount();

        // Obtener últimos 5 torneos
        List<TournamentDto> recentTournaments = tournamentService.getRecentTournaments(5);

        // Obtener últimos 5 jugadores
        List<PlayerResponseDto> recentPlayers = playerService.getRecentPlayers(5);

        // Obtener partidos en curso
        List<MatchDto> matchesInProgress = matchService.getMatchesByStatus(MatchStatus.EN_CURSO);

        // Obtener próximos partidos
        List<MatchDto> upcomingMatches = matchService.getUpcomingMatches(5);

        // Obtener torneos con brackets activos (используем существующий метод)
        List<TournamentDto> activeTournaments = tournamentService.getTournamentsWithActiveBrackets();

        model.addAttribute("adminName", owner.getFirstName());
        model.addAttribute("totalPlayers", totalPlayers);
        model.addAttribute("totalOwners", totalOwners);
        model.addAttribute("totalTournaments", totalTournaments);
        model.addAttribute("totalWaitlist", totalWaitlist);
        model.addAttribute("recentTournaments", recentTournaments);
        model.addAttribute("recentPlayers", recentPlayers);
        model.addAttribute("matchesInProgress", matchesInProgress);
        model.addAttribute("upcomingMatches", upcomingMatches);
        model.addAttribute("activeTournaments", activeTournaments);

        return "admin/panel";
    }

    @GetMapping("/tournaments")
    public String listTournaments(Model model) {
        log.info("Listing all tournaments for admin");

        List<TournamentDto> tournaments = tournamentService.getAllTournaments();
        model.addAttribute("tournaments", tournaments);

        return "admin/tournaments/list";
    }

    @GetMapping("/tournaments/new")
    public String showCreateForm(Model model) {
        log.info("Showing tournament creation form");

        model.addAttribute("tournament", new TournamentDto());
        // Используем getActiveClubsForAdmin() вместо getAllActiveClubs()
        model.addAttribute("clubs", clubService.getActiveClubsForAdmin());
        model.addAttribute("genderFormats", Arrays.asList(GenderFormat.values()));
        model.addAttribute("tournamentTypes", Arrays.asList(TournamentType.values()));
        model.addAttribute("tournamentStatuses", Arrays.asList(TournamentStatus.values()));
        model.addAttribute("niveles", getNiveles());
        model.addAttribute("modalidades", Modalidad.values());

        return "admin/tournaments/form";
    }

    @PostMapping("/tournaments")
    public String createTournament(@Valid @ModelAttribute("tournament") TournamentDto tournamentDto,
                                   BindingResult bindingResult,
                                   @AuthenticationPrincipal Owner owner,
                                   RedirectAttributes redirectAttributes,
                                   Model model) {

        log.info("Creating new tournament: {}", tournamentDto.getNombre());

        if (bindingResult.hasErrors()) {
            log.warn("Validation errors: {}", bindingResult.getAllErrors());
            model.addAttribute("clubs", clubService.getActiveClubsForAdmin());
            model.addAttribute("genderFormats", Arrays.asList(GenderFormat.values()));
            model.addAttribute("tournamentTypes", Arrays.asList(TournamentType.values()));
            model.addAttribute("tournamentStatuses", Arrays.asList(TournamentStatus.values()));
            model.addAttribute("niveles", getNiveles());
            model.addAttribute("modalidades", Modalidad.values());
            return "admin/tournaments/form";
        }

        try {
            TournamentDto created = tournamentService.createTournament(tournamentDto, owner.getId());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Турнир \"" + created.getNombre() + "\" успешно создан");
            return "redirect:/admin/tournaments/" + created.getId();
        } catch (Exception e) {
            log.error("Error creating tournament", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка при создании турнира: " + e.getMessage());
            return "redirect:/admin/tournaments/new";
        }
    }

    @GetMapping("/tournaments/{id}")
    public String viewTournament(@PathVariable Long id, Model model) {
        log.info("Viewing tournament details: {}", id);

        TournamentDto tournament = tournamentService.getTournamentById(id)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));

        // Проверяем, есть ли активный турнир "Король Корта"
        List<TournamentKingOfCourt> activeKings = tournamentKingOfCourtRepository.findAllByTournamentIdAndIsActiveTrue(id);
        if (!activeKings.isEmpty()) {
            TournamentKingOfCourt king = activeKings.get(0); // Берем первый активный
            model.addAttribute("kingOfCourtActive", true);
            model.addAttribute("kingOfCourtId", king.getId());
        } else {
            model.addAttribute("kingOfCourtActive", false);
        }

        model.addAttribute("tournament", tournament);
        model.addAttribute("registrations", tournamentService.getRegistrationsByTournament(id));

        return "admin/tournaments/details";
    }

    @GetMapping("/tournaments/{id}/edit")
    public String showEditForm(@PathVariable Long id, Model model) {
        log.info("Editing tournament: {}", id);

        TournamentDto tournament = tournamentService.getTournamentById(id)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));

        model.addAttribute("tournament", tournament);
        model.addAttribute("clubs", clubService.getActiveClubsForAdmin());
        model.addAttribute("genderFormats", Arrays.asList(GenderFormat.values()));
        model.addAttribute("tournamentTypes", Arrays.asList(TournamentType.values()));
        model.addAttribute("tournamentStatuses", Arrays.asList(TournamentStatus.values()));
        model.addAttribute("niveles", getNiveles());
        model.addAttribute("modalidades", Modalidad.values());

        return "admin/tournaments/form";
    }

    @PostMapping("/tournaments/{id}/edit")
    public String updateTournament(@PathVariable Long id,
                                   @Valid @ModelAttribute("tournament") TournamentDto tournamentDto,
                                   BindingResult bindingResult,
                                   RedirectAttributes redirectAttributes,
                                   Model model) {

        log.info("Updating tournament: {}", id);

        if (bindingResult.hasErrors()) {
            model.addAttribute("clubs", clubService.getActiveClubsForAdmin());
            model.addAttribute("genderFormats", Arrays.asList(GenderFormat.values()));
            model.addAttribute("tournamentTypes", Arrays.asList(TournamentType.values()));
            model.addAttribute("tournamentStatuses", Arrays.asList(TournamentStatus.values()));
            model.addAttribute("niveles", getNiveles());
            return "admin/tournaments/form";
        }

        try {
            TournamentDto updated = tournamentService.updateTournament(id, tournamentDto)
                    .orElseThrow(() -> new RuntimeException("Tournament not found"));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Турнир \"" + updated.getNombre() + "\" успешно обновлен");
            return "redirect:/admin/tournaments/" + id;
        } catch (Exception e) {
            log.error("Error updating tournament", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка при обновлении турнира: " + e.getMessage());
            return "redirect:/admin/tournaments/" + id + "/edit";
        }
    }

    @PostMapping("/tournaments/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @RequestParam TournamentStatus status,
                               @AuthenticationPrincipal Owner owner,
                               RedirectAttributes redirectAttributes) {
        try {
            tournamentService.updateTournamentStatus(id, status, owner.getId());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Статус турнира изменен на " + status.getValue());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка при изменении статуса: " + e.getMessage());
        }
        return "redirect:/admin/tournaments/" + id;
    }

    @PostMapping("/tournaments/{id}/delete")
    public String deleteTournament(@PathVariable Long id,
                                   RedirectAttributes redirectAttributes) {
        try {
            tournamentService.deleteTournament(id);
            redirectAttributes.addFlashAttribute("successMessage", "Турнир удален");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка при удалении: " + e.getMessage());
        }
        return "redirect:/admin/tournaments";
    }

    @PostMapping("/tournaments/{id}/deactivate")
    public String deactivateTournament(@PathVariable Long id,
                                       RedirectAttributes redirectAttributes) {
        log.info("Deactivating tournament: {}", id);
        try {
            tournamentService.deactivateTournament(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Турнир деактивирован. Теперь его можно удалить.");
        } catch (Exception e) {
            log.error("Error deactivating tournament", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка при деактивации: " + e.getMessage());
        }
        return "redirect:/admin/tournaments/" + id;
    }

    private List<Nivel> getNiveles() {
        return Arrays.asList(Nivel.values());
    }
}