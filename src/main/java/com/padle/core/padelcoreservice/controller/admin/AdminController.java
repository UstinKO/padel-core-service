package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.annotation.Timed;
import com.padle.core.padelcoreservice.dto.MatchDto;
import com.padle.core.padelcoreservice.dto.PlayerResponseDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.TournamentKingOfCourt;
import com.padle.core.padelcoreservice.model.enums.*;
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import com.padle.core.padelcoreservice.service.ClubService;
import com.padle.core.padelcoreservice.service.DoubleTournamentRegistrationService;
import com.padle.core.padelcoreservice.service.MatchService;
import com.padle.core.padelcoreservice.service.OwnerService;
import com.padle.core.padelcoreservice.service.PlayerService;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.service.americano.AmericanoService;
import com.padle.core.padelcoreservice.service.americano.TeamAmericanoService;
import com.padle.core.padelcoreservice.service.americano.TeamPlayoffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final AmericanoService americanoService;
    private final TeamAmericanoService teamAmericanoService;
    private final TeamPlayoffService teamPlayoffService;
    private final DoubleTournamentRegistrationService doubleRegistrationService;

    @GetMapping
    public String adminPanel(Model model, @AuthenticationPrincipal Owner owner) {
        log.info("Accessing admin panel for owner: {}", owner.getEmail());

        long totalPlayers = playerService.contarJugadoresActivos();
        long totalOwners = ownerService.getTotalActiveOwners();
        long totalTournaments = tournamentService.getTotalActiveTournaments();
        long totalWaitlist = tournamentService.getTotalWaitlistCount();

        List<TournamentDto> recentTournaments = tournamentService.getRecentTournaments(5);

        // Добавляем флаг isOwner для каждого турнира в отдельный список
        List<Map<String, Object>> recentTournamentsWithFlags = recentTournaments.stream()
                .map(t -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("tournament", t);
                    map.put("isOwner", owner.isSuperAdmin() ||
                            (t.getOwnerId() != null && t.getOwnerId().equals(owner.getId())));
                    return map;
                })
                .collect(Collectors.toList());

        List<PlayerResponseDto> recentPlayers = playerService.getRecentPlayers(5);
        List<MatchDto> matchesInProgress = matchService.getMatchesByStatus(MatchStatus.EN_CURSO);
        List<MatchDto> upcomingMatches = matchService.getUpcomingMatches(5);
        List<TournamentDto> activeTournaments = tournamentService.getTournamentsWithActiveBrackets();

        model.addAttribute("adminName", owner.getFirstName());
        model.addAttribute("totalPlayers", totalPlayers);
        model.addAttribute("totalOwners", totalOwners);
        model.addAttribute("totalTournaments", totalTournaments);
        model.addAttribute("totalWaitlist", totalWaitlist);
        model.addAttribute("recentTournaments", recentTournaments);
        model.addAttribute("recentTournamentsWithFlags", recentTournamentsWithFlags);
        model.addAttribute("recentPlayers", recentPlayers);
        model.addAttribute("matchesInProgress", matchesInProgress);
        model.addAttribute("upcomingMatches", upcomingMatches);
        model.addAttribute("activeTournaments", activeTournaments);
        model.addAttribute("isSuperAdmin", owner.isSuperAdmin());
        model.addAttribute("isAdminRole", owner.isAdminRole());

        return "admin/panel";
    }

    @GetMapping("/tournaments")
    public String listTournaments(Model model, @AuthenticationPrincipal Owner owner) {
        log.info("Listing tournaments for owner: {}", owner.getEmail());

        // Получаем ВСЕ турниры
        List<TournamentDto> allTournaments = tournamentService.getAllTournaments();

        // Добавляем флаги для каждого турнира
        List<Map<String, Object>> tournamentsWithFlags = allTournaments.stream()
                .map(t -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("tournament", t);

                    boolean isOwner = owner.isSuperAdmin() ||
                            (t.getOwnerId() != null && t.getOwnerId().equals(owner.getId()));
                    map.put("isOwner", isOwner);

                    // ADMIN: видит все турниры, но не может копировать/редактировать/удалять
                    map.put("canCopy", !owner.isAdminRole());
                    map.put("canView", isOwner || owner.isAdminRole());

                    return map;
                })
                .collect(Collectors.toList());

        model.addAttribute("tournaments", tournamentsWithFlags);
        model.addAttribute("isSuperAdmin", owner.isSuperAdmin());
        model.addAttribute("isAdminRole", owner.isAdminRole());

        return "admin/tournaments/list";
    }

    @PostMapping("/tournaments")
    public String createTournament(@Valid @ModelAttribute("tournament") TournamentDto tournamentDto,
                                   BindingResult bindingResult,
                                   @AuthenticationPrincipal Owner owner,
                                   RedirectAttributes redirectAttributes,
                                   Model model) {

        if (owner.isAdminRole()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No tienes permiso para crear torneos");
            return "redirect:/admin/tournaments";
        }
        log.info("Creating new tournament: {} by owner: {}", tournamentDto.getNombre(), owner.getEmail());

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

    @GetMapping("/tournaments/new")
    public String newTournamentForm(Model model,
                                    @AuthenticationPrincipal Owner owner,
                                    org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (owner.isAdminRole()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No tienes permiso para crear torneos");
            return "redirect:/admin/tournaments";
        }
        log.info("Showing new tournament form for owner: {}", owner.getEmail());

        model.addAttribute("tournament", new TournamentDto());
        model.addAttribute("clubs", clubService.getActiveClubsForAdmin());
        model.addAttribute("genderFormats", Arrays.asList(GenderFormat.values()));
        model.addAttribute("tournamentTypes", Arrays.asList(TournamentType.values()));
        model.addAttribute("tournamentStatuses", Arrays.asList(TournamentStatus.values()));
        model.addAttribute("niveles", getNiveles());
        model.addAttribute("modalidades", Modalidad.values());

        return "admin/tournaments/form";
    }

    @Timed(
            name = "admin.tournament.details.time",
            description = "Time taken to load admin tournament details page",
            tags = {"service=admin", "operation=tournamentDetails"}
    )
    @GetMapping("/tournaments/{id}")
    public String viewTournament(@PathVariable Long id,
                                 Model model,
                                 @AuthenticationPrincipal Owner owner) {
        log.info("Viewing tournament details: {} by owner: {}", id, owner.getEmail());

        TournamentDto tournament = tournamentService.getTournamentDtoById(id)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));

        // ADMIN и SUPER_ADMIN видят все турниры; ORGANIZER — только свои
        if (!owner.canViewAllTournaments()) {
            Long tournamentOwnerId = tournament.getOwnerId();
            if (tournamentOwnerId == null || !tournamentOwnerId.equals(owner.getId())) {
                throw new SecurityException("No tienes permiso para ver este torneo");
            }
        }

        List<TournamentKingOfCourt> activeKings = tournamentKingOfCourtRepository.findAllByTournamentIdAndIsActiveTrue(id);
        if (!activeKings.isEmpty()) {
            TournamentKingOfCourt king = activeKings.get(0);
            model.addAttribute("kingOfCourtActive", true);
            model.addAttribute("kingOfCourtId", king.getId());
        } else {
            model.addAttribute("kingOfCourtActive", false);
        }

        model.addAttribute("tournament", tournament);
        model.addAttribute("registrations", tournamentService.getRegistrationsByTournament(id));
        model.addAttribute("isSuperAdmin", owner.isSuperAdmin());
        model.addAttribute("isAdminRole", owner.isAdminRole());

        if (tournament.getTipo() == TournamentType.AMERICANO
                && tournament.getModalidad() == Modalidad.INDIVIDUAL) {
            boolean isInit = americanoService.isInitialized(id);
            model.addAttribute("isInitialized", isInit);
            if (isInit) {
                model.addAttribute("activePlayers", americanoService.getActivePlayersCount(id));
            }
        }

        if (tournament.getTipo() == TournamentType.AMERICANO
                && tournament.getModalidad() == Modalidad.DOBLES) {
            boolean isTeamInit = teamAmericanoService.isInitialized(id);
            model.addAttribute("isTeamAmericanoInitialized", isTeamInit);
            if (isTeamInit) {
                // Турнир уже запущен по Round Robin — не трогаем
                model.addAttribute("teamAmericanoRanking", teamAmericanoService.getRanking(id));
            } else {
                // Новый формат: квалификация + плей-офф
                model.addAttribute("isPlayoffQualStarted", teamPlayoffService.isQualificationStarted(id));
                model.addAttribute("isPlayoffStarted", teamPlayoffService.isPlayoffStarted(id));
                model.addAttribute("playoffTeamsCount", teamPlayoffService.getTeams(id).size());
            }
        }

        if (tournament.getTipo() == TournamentType.AMERICANO_TEAMS) {
            model.addAttribute("isTeamAmericanoInitialized", false);
            boolean qualStarted   = teamPlayoffService.isQualificationStarted(id);
            boolean playoffStarted = teamPlayoffService.isPlayoffStarted(id);
            model.addAttribute("isPlayoffQualStarted", qualStarted);
            model.addAttribute("isPlayoffStarted", playoffStarted);
            model.addAttribute("playoffTeamsCount", teamPlayoffService.getTeams(id).size());
        }

        return "admin/tournaments/details";
    }

    @GetMapping("/tournaments/{id}/edit")
    public String showEditForm(@PathVariable Long id,
                               Model model,
                               @AuthenticationPrincipal Owner owner,
                               RedirectAttributes redirectAttributes) {
        if (owner.isAdminRole()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No tienes permiso para editar torneos");
            return "redirect:/admin/tournaments/" + id;
        }
        log.info("Editing tournament: {} by owner: {}", id, owner.getEmail());

        TournamentDto tournament = tournamentService.getTournamentDtoById(id)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));

        // Проверяем права для ORGANIZER
        if (!owner.isSuperAdmin() && !tournament.getOwnerId().equals(owner.getId())) {
            throw new SecurityException("No tienes permiso para editar este torneo");
        }

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
                                   @AuthenticationPrincipal Owner owner,
                                   RedirectAttributes redirectAttributes,
                                   Model model) {

        if (owner.isAdminRole()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No tienes permiso para editar torneos");
            return "redirect:/admin/tournaments/" + id;
        }
        log.info("Updating tournament: {} by owner: {}", id, owner.getEmail());

        if (bindingResult.hasErrors()) {
            model.addAttribute("clubs", clubService.getActiveClubsForAdmin());
            model.addAttribute("genderFormats", Arrays.asList(GenderFormat.values()));
            model.addAttribute("tournamentTypes", Arrays.asList(TournamentType.values()));
            model.addAttribute("tournamentStatuses", Arrays.asList(TournamentStatus.values()));
            model.addAttribute("niveles", getNiveles());
            return "admin/tournaments/form";
        }

        try {
            TournamentDto updated = tournamentService.updateTournament(id, tournamentDto, owner.getId(), owner.isSuperAdmin())
                    .orElseThrow(() -> new RuntimeException("Tournament not found"));
            redirectAttributes.addFlashAttribute("successMessage",
                    "Турнир \"" + updated.getNombre() + "\" успешно обновлен");
            return "redirect:/admin/tournaments/" + id;
        } catch (SecurityException e) {
            log.error("Security error updating tournament", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/tournaments";
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
            // ADMIN может менять статус любого турнира (как SUPER_ADMIN)
            tournamentService.updateTournamentStatus(id, status, owner.getId(), owner.getId(), owner.canViewAllTournaments());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Статус турнира изменен на " + status.getValue());
        } catch (SecurityException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка при изменении статуса: " + e.getMessage());
        }
        return "redirect:/admin/tournaments/" + id;
    }

    @PostMapping("/tournaments/{id}/delete")
    public String deleteTournament(@PathVariable Long id,
                                   @AuthenticationPrincipal Owner owner,
                                   RedirectAttributes redirectAttributes) {
        if (owner.isAdminRole()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No tienes permiso para eliminar torneos");
            return "redirect:/admin/tournaments/" + id;
        }
        try {
            // Проверяем права
            if (!owner.isSuperAdmin()) {
                TournamentDto tournament = tournamentService.getTournamentDtoById(id)
                        .orElseThrow(() -> new RuntimeException("Tournament not found"));
                if (!tournament.getOwnerId().equals(owner.getId())) {
                    throw new SecurityException("No tienes permiso para eliminar este torneo");
                }
            }
            tournamentService.deleteTournament(id);
            redirectAttributes.addFlashAttribute("successMessage", "Турнир удален");
        } catch (SecurityException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка при удалении: " + e.getMessage());
        }
        return "redirect:/admin/tournaments";
    }

    @PostMapping("/tournaments/{id}/deactivate")
    public String deactivateTournament(@PathVariable Long id,
                                       @AuthenticationPrincipal Owner owner,
                                       RedirectAttributes redirectAttributes) {
        if (owner.isAdminRole()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No tienes permiso para desactivar torneos");
            return "redirect:/admin/tournaments/" + id;
        }
        log.info("Deactivating tournament: {} by owner: {}", id, owner.getEmail());
        try {
            // Проверяем права
            if (!owner.isSuperAdmin()) {
                TournamentDto tournament = tournamentService.getTournamentDtoById(id)
                        .orElseThrow(() -> new RuntimeException("Tournament not found"));
                if (!tournament.getOwnerId().equals(owner.getId())) {
                    throw new SecurityException("No tienes permiso para desactivar este torneo");
                }
            }
            tournamentService.deactivateTournament(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Турнир деактивирован. Теперь его можно удалить.");
        } catch (SecurityException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            log.error("Error deactivating tournament", e);
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Ошибка при деактивации: " + e.getMessage());
        }
        return "redirect:/admin/tournaments/" + id;
    }

    @PostMapping("/tournaments/{tournamentId}/move-to-waitlist/{playerId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> moveToWaitlist(
            @PathVariable Long tournamentId,
            @PathVariable Long playerId,
            @AuthenticationPrincipal Owner owner) {

        Map<String, Object> result = new HashMap<>();
        log.info("Moving player {} from main to waitlist in tournament {} by owner: {}", playerId, tournamentId, owner.getEmail());

        try {
            // ADMIN и SUPER_ADMIN управляют составом любого турнира
            if (!owner.canViewAllTournaments()) {
                TournamentDto tournament = tournamentService.getTournamentDtoById(tournamentId)
                        .orElseThrow(() -> new RuntimeException("Tournament not found"));
                if (!tournament.getOwnerId().equals(owner.getId())) {
                    throw new SecurityException("No tienes permiso para gestionar este torneo");
                }
            }
            tournamentService.moveToWaitlist(tournamentId, playerId);
            result.put("success", true);
            result.put("message", "Jugador movido a la lista de espera");
        } catch (SecurityException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("Error moving player to waitlist", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/tournaments/{tournamentId}/move-to-main/{playerId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> moveToMain(
            @PathVariable Long tournamentId,
            @PathVariable Long playerId,
            @AuthenticationPrincipal Owner owner) {

        Map<String, Object> result = new HashMap<>();
        log.info("Moving player {} from waitlist to main in tournament {} by owner: {}", playerId, tournamentId, owner.getEmail());

        try {
            // ADMIN и SUPER_ADMIN управляют составом любого турнира
            if (!owner.canViewAllTournaments()) {
                TournamentDto tournament = tournamentService.getTournamentDtoById(tournamentId)
                        .orElseThrow(() -> new RuntimeException("Tournament not found"));
                if (!tournament.getOwnerId().equals(owner.getId())) {
                    throw new SecurityException("No tienes permiso para gestionar este torneo");
                }
            }
            tournamentService.moveToMain(tournamentId, playerId);
            result.put("success", true);
            result.put("message", "Jugador movido al torneo principal");
        } catch (SecurityException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("Error moving player to main", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/tournaments/{tournamentId}/pair-solo")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> pairSoloPlayers(
            @PathVariable Long tournamentId,
            @RequestParam Long regId1,
            @RequestParam Long regId2) {

        Map<String, Object> result = new HashMap<>();
        try {
            doubleRegistrationService.adminPairSoloPlayers(regId1, regId2);
            result.put("success", true);
            result.put("message", "¡Pareja formada con éxito! Ambos jugadores están ahora confirmados.");
        } catch (com.padle.core.padelcoreservice.exception.TournamentRegistrationException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            log.error("Error pairing solo players in tournament {}", tournamentId, e);
            result.put("success", false);
            result.put("message", "Error al emparejar jugadores");
        }
        return ResponseEntity.ok(result);
    }

    private List<Nivel> getNiveles() {
        return Arrays.asList(Nivel.values());
    }
}