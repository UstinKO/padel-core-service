package com.padle.core.padelcoreservice.controller.view.americano;

import com.padle.core.padelcoreservice.dto.PlayerResponseDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchSuggestionDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoRoundDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoTeamDto;
import com.padle.core.padelcoreservice.dto.americano.TeamAmericanoRankingDto;
import com.padle.core.padelcoreservice.dto.americano.TeamPlayoffAddTeamRequest;
import com.padle.core.padelcoreservice.dto.americano.TeamPlayoffTeamRequest;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoRound;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentPhase;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.service.PlayerService;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.service.americano.TeamPlayoffService;
import com.padle.core.padelcoreservice.service.americano.TeamPlayoffReadService;
import com.padle.core.padelcoreservice.service.conduct.TournamentConductHistory;
import com.padle.core.padelcoreservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/tournaments/team-playoff")
@RequiredArgsConstructor
@Slf4j
public class TeamPlayoffViewController {

    private final TeamPlayoffService playoffService;
    private final TeamPlayoffReadService readService;
    private final TournamentService tournamentService;
    private final AmericanoMatchRepository matchRepository;
    private final TournamentAccessService tournamentAccessService;
    private final TournamentConductHistory conductHistory;
    private final PlayerService playerService;
    private final MessageSource messageSource;

    // ══════════════════════════════════════════════════════════════════════
    // ПУБЛИЧНАЯ СТРАНИЦА
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/{tournamentId}")
    public String viewPublic(@PathVariable Long tournamentId,
                             Model model,
                             Authentication auth) {

        TournamentDto tournament = tournamentService.getTournamentDtoById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

        boolean qualStarted = playoffService.isQualificationStarted(tournamentId);
        boolean playoffStarted = playoffService.isPlayoffStarted(tournamentId);

        PlayerPadel player = SecurityUtils.extractPlayer(auth != null ? auth.getPrincipal() : null);

        model.addAttribute("tournament", tournament);
        model.addAttribute("qualStarted", qualStarted);
        model.addAttribute("playoffStarted", playoffStarted);
        model.addAttribute("isAuthenticated", auth != null);
        model.addAttribute("player", player);
        model.addAttribute("currentPlayerId", player != null ? player.getId() : null);

        if (qualStarted) {
            TeamAmericanoRankingDto ranking = playoffService.getQualRanking(tournamentId);
            List<AmericanoRound> qualRounds = playoffService.getQualRounds(tournamentId);
            List<AmericanoRoundDto> qualRoundDtos = buildRoundDtos(qualRounds);
            applyQualMatchOrdinals(tournamentId, qualRoundDtos);
            model.addAttribute("ranking", ranking);
            model.addAttribute("qualRounds", splitQualificationWaves(qualRoundDtos));
        }

        if (playoffStarted) {
            List<AmericanoRound> playoffRounds = playoffService.getPlayoffRounds(tournamentId);
            model.addAttribute("playoffRounds", buildRoundDtos(playoffRounds));
        }

        return "tournaments/team-playoff/view";
    }

    // ══════════════════════════════════════════════════════════════════════
    // ADMIN — ГЛАВНАЯ СТРАНИЦА УПРАВЛЕНИЯ
    // ══════════════════════════════════════════════════════════════════════

    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    @GetMapping("/admin/{tournamentId}")
    public String viewAdmin(@PathVariable Long tournamentId, Model model,
                            @AuthenticationPrincipal Owner owner) {
        tournamentAccessService.assertCanManageTournament(owner, tournamentId);

        TournamentDto tournament = tournamentService.getActiveTournamentById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

        model.addAttribute("conductVersion", conductHistory.revision(tournamentId));
        boolean qualStarted   = playoffService.isQualificationStarted(tournamentId);
        boolean playoffStarted = playoffService.isPlayoffStarted(tournamentId);

        List<AmericanoTeamDto> teams = playoffService.getTeamDtos(tournamentId);

        model.addAttribute("tournament", tournament);
        model.addAttribute("teams", teams);
        model.addAttribute("qualStarted", qualStarted);
        model.addAttribute("playoffStarted", playoffStarted);

        if (qualStarted) {
            TeamAmericanoRankingDto ranking = playoffService.getQualRanking(tournamentId);
            List<AmericanoRound> qualRounds = playoffService.getQualRounds(tournamentId);
            List<AmericanoRoundDto> qualRoundDtos = buildRoundDtos(qualRounds);
            applyQualMatchOrdinals(tournamentId, qualRoundDtos);
            model.addAttribute("ranking", ranking);
            model.addAttribute("qualRounds", splitQualificationWaves(qualRoundDtos));
            // Доступность кнопки "Начать плей-офф" до полного завершения
            // квалификации — см. TeamPlayoffService.canInitPlayoff.
            model.addAttribute("canInitPlayoff", !playoffStarted && playoffService.canInitPlayoff(tournamentId));
        }

        if (playoffStarted) {
            List<AmericanoMatch> playoffMatches = playoffService.getPlayoffMatches(tournamentId);
            model.addAttribute("playoffMatches", playoffMatches.stream()
                    .map(playoffService::toMatchDto).toList());
            List<AmericanoRound> playoffRounds = playoffService.getPlayoffRounds(tournamentId);
            model.addAttribute("playoffRounds", buildRoundDtos(playoffRounds));
        }

        return "admin/americano/tournament-playoff";
    }

    // ══════════════════════════════════════════════════════════════════════
    // ADMIN — УПРАВЛЕНИЕ КОМАНДАМИ
    // ══════════════════════════════════════════════════════════════════════

    @PostMapping("/admin/{tournamentId}/teams/add")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    public String addTeam(@PathVariable Long tournamentId,
                          @ModelAttribute TeamPlayoffAddTeamRequest req,
                          @RequestParam(required = false) String returnTo,
                          RedirectAttributes ra,
                          @AuthenticationPrincipal Owner currentOwner) {
        tournamentAccessService.assertCanManageTournament(currentOwner, tournamentId);
        // Форма «+ Добавить пару вручную» на странице оплат (returnTo=payments) —
        // та же логика добавления команды, но координатор возвращается на страницу оплат, а не
        // на страницу Team Playoff; у двух страниц разные имена flash-атрибутов для сообщений.
        boolean returnToPayments = "payments".equals(returnTo);
        try {
            playoffService.addTeam(tournamentId, req);
            ra.addFlashAttribute(returnToPayments ? "successMessage" : "success", "Equipo agregado correctamente");
        } catch (InvalidStateException | ResourceNotFoundException e) {
            log.error("Error adding team to {}: {}", tournamentId, e.getMessage());
            ra.addFlashAttribute(returnToPayments ? "errorMessage" : "error", "Error al agregar equipo: " + e.getMessage());
        } catch (DataAccessException e) {
            log.error("Не удалось сохранить команду Team Playoff в турнире {}", tournamentId, e);
            ra.addFlashAttribute(returnToPayments ? "errorMessage" : "error",
                    messageSource.getMessage("admin.team_playoff.add_team.failure", null, LocaleContextHolder.getLocale()));
        }
        return returnToPayments
                ? "redirect:/admin/tournaments/" + tournamentId + "/payments"
                : "redirect:/tournaments/team-playoff/admin/" + tournamentId;
    }

    /**
     * Поиск активных игроков (имя/фамилия/телефон/email) для автодополнения в форме
     * «Добавить команду».
     */
    @GetMapping("/admin/{tournamentId}/players/search")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    @ResponseBody
    public List<PlayerResponseDto> searchPlayers(@PathVariable Long tournamentId,
                                                  @RequestParam String query,
                                                  @AuthenticationPrincipal Owner currentOwner) {
        tournamentAccessService.assertCanManageTournament(currentOwner, tournamentId);
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        return playerService.searchActivePlayers(query.trim());
    }

    @PostMapping("/admin/teams/{teamId}/update")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    public String updateTeam(@PathVariable Long teamId,
                             @ModelAttribute TeamPlayoffTeamRequest req,
                             @RequestParam Long tournamentId,
                             RedirectAttributes ra,
                             @AuthenticationPrincipal Owner currentOwner) {
        tournamentAccessService.assertCanManageAmericanoTeam(currentOwner, teamId);
        try {
            playoffService.updateTeam(teamId, req);
            ra.addFlashAttribute("success", "Equipo actualizado correctamente");
        } catch (Exception e) {
            log.error("Error updating team {}: {}", teamId, e.getMessage());
            ra.addFlashAttribute("error", "Error al actualizar: " + e.getMessage());
        }
        return "redirect:/tournaments/team-playoff/admin/" + tournamentId;
    }

    @PostMapping("/admin/teams/{teamId}/delete")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    public String deleteTeam(@PathVariable Long teamId,
                             @RequestParam Long tournamentId,
                             RedirectAttributes ra,
                             @AuthenticationPrincipal Owner currentOwner) {
        tournamentAccessService.assertCanManageAmericanoTeam(currentOwner, teamId);
        try {
            playoffService.removeTeam(teamId);
            ra.addFlashAttribute("success", "Equipo eliminado");
        } catch (Exception e) {
            log.error("Error deleting team {}: {}", teamId, e.getMessage());
            ra.addFlashAttribute("error", "Error al eliminar: " + e.getMessage());
        }
        return "redirect:/tournaments/team-playoff/admin/" + tournamentId;
    }

    @PostMapping("/admin/teams/{teamId}/no-show")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    public String markTeamNoShow(@PathVariable Long teamId,
                                  @RequestParam Long tournamentId,
                                  RedirectAttributes ra,
                                  @AuthenticationPrincipal Owner currentOwner) {
        tournamentAccessService.assertCanManageAmericanoTeam(currentOwner, teamId);
        try {
            playoffService.markNoShow(teamId);
            ra.addFlashAttribute("success", "Equipo marcado como no presentado");
        } catch (InvalidStateException | ResourceNotFoundException e) {
            log.warn("Не удалось отметить команду {} как «не пришла»: {}", teamId, e.getMessage());
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/tournaments/team-playoff/admin/" + tournamentId;
    }

    @PostMapping("/admin/teams/{teamId}/no-show/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    public String cancelTeamNoShow(@PathVariable Long teamId,
                                    @RequestParam Long tournamentId,
                                    RedirectAttributes ra,
                                    @AuthenticationPrincipal Owner currentOwner) {
        tournamentAccessService.assertCanManageAmericanoTeam(currentOwner, teamId);
        try {
            playoffService.cancelNoShow(teamId);
            ra.addFlashAttribute("success", "Se canceló la marca de no presentado");
        } catch (InvalidStateException | ResourceNotFoundException e) {
            log.warn("Не удалось снять отметку «не пришла» с команды {}: {}", teamId, e.getMessage());
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/tournaments/team-playoff/admin/" + tournamentId;
    }

    // ══════════════════════════════════════════════════════════════════════
    // ADMIN — ИНИЦИАЛИЗАЦИЯ ФАЗ
    // ══════════════════════════════════════════════════════════════════════

    @PostMapping("/admin/{tournamentId}/import-registrations")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    public String importRegistrations(@PathVariable Long tournamentId, RedirectAttributes ra,
                                      @AuthenticationPrincipal Owner currentOwner) {
        tournamentAccessService.assertCanManageTournament(currentOwner, tournamentId);
        try {
            int count = playoffService.importFromRegistrations(tournamentId);
            ra.addFlashAttribute("success", "Se importaron " + count + " equipos desde las inscripciones");
        } catch (Exception e) {
            log.error("Error importing registrations {}: {}", tournamentId, e.getMessage());
            ra.addFlashAttribute("error", "Error al importar: " + e.getMessage());
        }
        return "redirect:/tournaments/team-playoff/admin/" + tournamentId;
    }

    @PostMapping("/admin/{tournamentId}/init-qualification")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    public String initQualification(@PathVariable Long tournamentId,
                                    @RequestParam(defaultValue = "2") int courts,
                                    RedirectAttributes ra,
                                    @AuthenticationPrincipal Owner currentOwner) {
        tournamentAccessService.assertCanManageTournament(currentOwner, tournamentId);
        try {
            playoffService.initQualification(tournamentId, courts);
            ra.addFlashAttribute("success", "Fase de calificación inicializada");
        } catch (Exception e) {
            log.error("Error init qualification {}: {}", tournamentId, e.getMessage());
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/tournaments/team-playoff/admin/" + tournamentId;
    }

    @PostMapping("/admin/{tournamentId}/init-playoff")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    public String initPlayoff(@PathVariable Long tournamentId, RedirectAttributes ra,
                              @AuthenticationPrincipal Owner currentOwner) {
        tournamentAccessService.assertCanManageTournament(currentOwner, tournamentId);
        try {
            playoffService.initPlayoff(tournamentId);
            ra.addFlashAttribute("success", "Playoff inicializado");
        } catch (Exception e) {
            log.error("Error init playoff {}: {}", tournamentId, e.getMessage());
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/tournaments/team-playoff/admin/" + tournamentId;
    }

    /** Пересобрать плей-офф после правки результата квалификации. */
    @PostMapping("/admin/{tournamentId}/regenerate-playoff")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    public String regeneratePlayoff(@PathVariable Long tournamentId, @RequestParam long expectedConductVersion,
                                    RedirectAttributes ra, @AuthenticationPrincipal Owner currentOwner) {
        tournamentAccessService.assertCanManageTournament(currentOwner, tournamentId);
        try {
            playoffService.regeneratePlayoff(tournamentId);
            ra.addFlashAttribute("success", "Playoff reformado correctamente");
        } catch (Exception e) {
            log.error("Error regenerating playoff {}: {}", tournamentId, e.getMessage());
            ra.addFlashAttribute("error", messageSource.getMessage(e.getMessage(), null, e.getMessage(),
                    LocaleContextHolder.getLocale()));
        }
        return "redirect:/tournaments/team-playoff/admin/" + tournamentId;
    }

    // ══════════════════════════════════════════════════════════════════════
    // ADMIN — ВВОД РЕЗУЛЬТАТОВ
    // ══════════════════════════════════════════════════════════════════════

    @PostMapping("/admin/qual-matches/{matchId}/result")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    public String submitQualResult(@PathVariable Long matchId,
                                   @RequestParam int team1Games,
                                   @RequestParam int team2Games,
                                   @RequestParam Long tournamentId,
                                   RedirectAttributes ra,
                                   @AuthenticationPrincipal Owner currentOwner) {
        tournamentAccessService.assertCanManageAmericanoMatch(currentOwner, matchId);
        try {
            playoffService.submitQualResult(matchId, team1Games, team2Games);
            ra.addFlashAttribute("success", "Resultado guardado");
        } catch (InvalidStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error submitting qual result {}: {}", matchId, e.getMessage());
            ra.addFlashAttribute("error", "Error al guardar: " + e.getMessage());
        }
        return "redirect:/tournaments/team-playoff/admin/" + tournamentId;
    }

    @PostMapping("/admin/playoff-matches/{matchId}/result")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    public String submitPlayoffResult(@PathVariable Long matchId,
                                      @RequestParam int team1Games,
                                      @RequestParam int team2Games,
                                      @RequestParam Long tournamentId,
                                      @RequestParam(required = false) Long expectedTeam1,
                                      @RequestParam(required = false) Long expectedTeam2,
                                      RedirectAttributes ra,
                                      @AuthenticationPrincipal Owner currentOwner) {
        tournamentAccessService.assertCanManageAmericanoMatch(currentOwner, matchId);
        try {
            playoffService.submitPlayoffResultForPair(matchId, team1Games, team2Games, expectedTeam1, expectedTeam2);
            ra.addFlashAttribute("success", "Resultado guardado");
        } catch (InvalidStateException e) {
            ra.addFlashAttribute("error", messageSource.getMessage(e.getMessage(), null, e.getMessage(),
                    LocaleContextHolder.getLocale()));
        } catch (Exception e) {
            log.error("Error submitting playoff result {}: {}", matchId, e.getMessage());
            ra.addFlashAttribute("error", "Error al guardar: " + e.getMessage());
        }
        return "redirect:/tournaments/team-playoff/admin/" + tournamentId;
    }

    // ══════════════════════════════════════════════════════════════════════
    // REST API
    // ══════════════════════════════════════════════════════════════════════

    @PostMapping("/api/{tournamentId}/qual-matches")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createQualMatchApi(
            @PathVariable Long tournamentId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Owner currentOwner) {
        Map<String, Object> result = new HashMap<>();
        tournamentAccessService.assertCanManageTournament(currentOwner, tournamentId);
        try {
            Long team1Id = Long.valueOf(String.valueOf(body.get("team1Id")));
            Long team2Id = Long.valueOf(String.valueOf(body.get("team2Id")));
            int courtNumber = Integer.parseInt(String.valueOf(body.get("courtNumber")));
            AmericanoMatch match = playoffService.createQualificationMatch(tournamentId, team1Id, team2Id, courtNumber);
            result.put("success", true);
            result.put("match", playoffService.toMatchDto(match));
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /** Ставит пару в очередь без назначения корта. */
    @PostMapping("/api/{tournamentId}/qual-matches/queue")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> queueQualMatchApi(
            @PathVariable Long tournamentId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Owner currentOwner) {
        Map<String, Object> result = new HashMap<>();
        tournamentAccessService.assertCanManageTournament(currentOwner, tournamentId);
        try {
            Long team1Id = Long.valueOf(String.valueOf(body.get("team1Id")));
            Long team2Id = Long.valueOf(String.valueOf(body.get("team2Id")));
            AmericanoMatch match = playoffService.queueQualificationMatch(tournamentId, team1Id, team2Id);
            result.put("success", true);
            result.put("match", playoffService.toMatchDto(match));
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /** Назначает свободный корт матчу из очереди — матч выходит из очереди и переходит в IN_PROGRESS. */
    @PostMapping("/api/matches/{matchId}/assign-court")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> assignCourtToQueuedMatchApi(
            @PathVariable Long matchId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Owner currentOwner) {
        Map<String, Object> result = new HashMap<>();
        tournamentAccessService.assertCanManageAmericanoMatch(currentOwner, matchId);
        try {
            int courtNumber = Integer.parseInt(String.valueOf(body.get("courtNumber")));
            AmericanoMatch match = playoffService.assignCourtToQueuedMatch(matchId, courtNumber);
            result.put("success", true);
            result.put("match", playoffService.toMatchDto(match));
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /** Полностью убирает матч из очереди ожидания. */
    @PostMapping("/api/matches/{matchId}/queue/remove")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> removeFromQueueApi(@PathVariable Long matchId,
                                                                    @AuthenticationPrincipal Owner currentOwner) {
        Map<String, Object> result = new HashMap<>();
        tournamentAccessService.assertCanManageAmericanoMatch(currentOwner, matchId);
        try {
            playoffService.removeFromQueue(matchId);
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /** Меняет корт ещё не завершённого матча (ручное управление координатором). */
    @PostMapping("/api/matches/{matchId}/court")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> changeMatchCourtApi(
            @PathVariable Long matchId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Owner currentOwner) {
        Map<String, Object> result = new HashMap<>();
        tournamentAccessService.assertCanManageAmericanoMatch(currentOwner, matchId);
        try {
            int courtNumber = Integer.parseInt(String.valueOf(body.get("courtNumber")));
            AmericanoMatch match = playoffService.changeMatchCourt(matchId, courtNumber);
            result.put("success", true);
            result.put("match", playoffService.toMatchDto(match));
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /** Заменяет команду в паре ещё не завершённого матча. slot=1|2. Реванш не блокирует, только предупреждает. */
    @PostMapping("/api/matches/{matchId}/team")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> changeMatchTeamApi(
            @PathVariable Long matchId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal Owner currentOwner) {
        Map<String, Object> result = new HashMap<>();
        tournamentAccessService.assertCanManageAmericanoMatch(currentOwner, matchId);
        try {
            int slot = Integer.parseInt(String.valueOf(body.get("slot")));
            Long teamId = Long.valueOf(String.valueOf(body.get("teamId")));
            TeamPlayoffService.MatchTeamChangeResult changeResult = playoffService.changeMatchTeam(matchId, slot, teamId);
            result.put("success", true);
            result.put("match", playoffService.toMatchDto(changeResult.match()));
            result.put("warning", changeResult.warning());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/qual-matches/{matchId}/result")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitQualResultApi(
            @PathVariable Long matchId,
            @RequestBody Map<String, Integer> body,
            @AuthenticationPrincipal Owner currentOwner) {
        Map<String, Object> result = new HashMap<>();
        tournamentAccessService.assertCanManageAmericanoMatch(currentOwner, matchId);
        try {
            int g1 = body.getOrDefault("team1Games", 0);
            int g2 = body.getOrDefault("team2Games", 0);
            playoffService.submitQualResult(matchId, g1, g2);
            result.put("success", true);
            result.put("message", "Resultado guardado");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/playoff-matches/{matchId}/result")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitPlayoffResultApi(
            @PathVariable Long matchId,
            @RequestBody Map<String, Integer> body,
            @RequestParam(required = false) Long expectedTeam1,
            @RequestParam(required = false) Long expectedTeam2,
            @AuthenticationPrincipal Owner currentOwner) {
        Map<String, Object> result = new HashMap<>();
        tournamentAccessService.assertCanManageAmericanoMatch(currentOwner, matchId);
        try {
            int g1 = body.getOrDefault("team1Games", 0);
            int g2 = body.getOrDefault("team2Games", 0);
            playoffService.submitPlayoffResultForPair(matchId, g1, g2, expectedTeam1, expectedTeam2);
            result.put("success", true);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/{tournamentId}/ranking")
    @ResponseBody
    public ResponseEntity<TeamAmericanoRankingDto> getRankingApi(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(playoffService.getQualRanking(tournamentId));
    }

    /**
     * Доска кортов: статус каждого корта (свободен/занят + текущий матч), пул команд,
     * доступных для назначения прямо сейчас, и предложенные пары 2-го тура квалификации
     * (победитель-к-победителю/проигравший-к-проигравшему) — по одной на свободный корт.
     */
    @GetMapping("/api/{tournamentId}/court-board")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'ORGANIZER', 'CLUB_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCourtBoard(@PathVariable Long tournamentId,
                                                               @AuthenticationPrincipal Owner currentOwner) {
        tournamentAccessService.assertCanManageTournament(currentOwner, tournamentId);
        int courtsCount = playoffService.getConfiguredCourts(tournamentId);
        Map<Integer, AmericanoMatch> matchByCourt = playoffService.getActiveMatches(tournamentId).stream()
                .filter(m -> m.getCourtNumber() != null)
                .collect(Collectors.toMap(AmericanoMatch::getCourtNumber, m -> m, (a, b) -> a));
        Iterator<AmericanoMatchSuggestionDto> suggestions =
                playoffService.suggestSecondRoundPairings(tournamentId).iterator();

        List<Map<String, Object>> courts = new ArrayList<>();
        for (int courtNumber = 1; courtNumber <= courtsCount; courtNumber++) {
            AmericanoMatch match = matchByCourt.get(courtNumber);
            Map<String, Object> court = new LinkedHashMap<>();
            court.put("courtNumber", courtNumber);
            court.put("occupied", match != null);
            court.put("currentMatch", match != null ? playoffService.toMatchDto(match) : null);
            court.put("suggestedNextMatch", match == null && suggestions.hasNext() ? suggestions.next() : null);
            courts.add(court);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("courts", courts);
        result.put("availableTeams", playoffService.getAvailableTeamsForQualification(tournamentId));
        result.put("teamGroups", playoffService.getQualificationTeamGroups(tournamentId));
        result.put("queue", playoffService.getQueue(tournamentId));
        return ResponseEntity.ok(result);
    }

    // ══════════════════════════════════════════════════════════════════════
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ══════════════════════════════════════════════════════════════════════

    private List<AmericanoRoundDto> buildRoundDtos(List<AmericanoRound> rounds) {
        return readService.rounds(rounds);
    }

    private void applyQualMatchOrdinals(Long tournamentId, List<AmericanoRoundDto> rounds) {
        readService.qualificationOrdinals(tournamentId, rounds);
    }

    List<AmericanoRoundDto> splitQualificationWaves(List<AmericanoRoundDto> rounds) {
        return readService.qualificationWaves(rounds);
    }
}
