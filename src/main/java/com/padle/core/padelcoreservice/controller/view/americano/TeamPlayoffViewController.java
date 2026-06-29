package com.padle.core.padelcoreservice.controller.view.americano;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoRoundDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoTeamDto;
import com.padle.core.padelcoreservice.dto.americano.TeamAmericanoRankingDto;
import com.padle.core.padelcoreservice.dto.americano.TeamPlayoffTeamRequest;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoRound;
import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentPhase;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.service.americano.TeamPlayoffService;
import com.padle.core.padelcoreservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/tournaments/team-playoff")
@RequiredArgsConstructor
@Slf4j
public class TeamPlayoffViewController {

    private final TeamPlayoffService playoffService;
    private final TournamentService tournamentService;
    private final AmericanoMatchRepository matchRepository;

    // ══════════════════════════════════════════════════════════════════════
    // ПУБЛИЧНАЯ СТРАНИЦА
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/{tournamentId}")
    public String viewPublic(@PathVariable Long tournamentId,
                             Model model,
                             Authentication auth) {

        TournamentDto tournament = tournamentService.getActiveTournamentById(tournamentId)
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
            model.addAttribute("ranking", ranking);
            model.addAttribute("qualRounds", buildRoundDtos(qualRounds));
        }

        if (playoffStarted) {
            List<AmericanoMatch> playoffMatches = playoffService.getPlayoffMatches(tournamentId);
            model.addAttribute("playoffMatches", playoffMatches.stream()
                    .map(this::toMatchDto).toList());
        }

        return "tournaments/team-playoff/view";
    }

    // ══════════════════════════════════════════════════════════════════════
    // ADMIN — ГЛАВНАЯ СТРАНИЦА УПРАВЛЕНИЯ
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/admin/{tournamentId}")
    public String viewAdmin(@PathVariable Long tournamentId, Model model) {

        TournamentDto tournament = tournamentService.getActiveTournamentById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

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
            model.addAttribute("ranking", ranking);
            model.addAttribute("qualRounds", buildRoundDtos(qualRounds));
        }

        if (playoffStarted) {
            List<AmericanoMatch> playoffMatches = playoffService.getPlayoffMatches(tournamentId);
            model.addAttribute("playoffMatches", playoffMatches.stream()
                    .map(this::toMatchDto).toList());
            List<AmericanoRound> playoffRounds = playoffService.getPlayoffRounds(tournamentId);
            model.addAttribute("playoffRounds", buildRoundDtos(playoffRounds));
        }

        return "admin/americano/tournament-playoff";
    }

    // ══════════════════════════════════════════════════════════════════════
    // ADMIN — УПРАВЛЕНИЕ КОМАНДАМИ
    // ══════════════════════════════════════════════════════════════════════

    @PostMapping("/admin/{tournamentId}/teams/add")
    public String addTeam(@PathVariable Long tournamentId,
                          @ModelAttribute TeamPlayoffTeamRequest req,
                          RedirectAttributes ra) {
        try {
            playoffService.addTeam(tournamentId, req);
            ra.addFlashAttribute("success", "Equipo agregado correctamente");
        } catch (Exception e) {
            log.error("Error adding team to {}: {}", tournamentId, e.getMessage());
            ra.addFlashAttribute("error", "Error al agregar equipo: " + e.getMessage());
        }
        return "redirect:/tournaments/team-playoff/admin/" + tournamentId;
    }

    @PostMapping("/admin/teams/{teamId}/update")
    public String updateTeam(@PathVariable Long teamId,
                             @ModelAttribute TeamPlayoffTeamRequest req,
                             @RequestParam Long tournamentId,
                             RedirectAttributes ra) {
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
    public String deleteTeam(@PathVariable Long teamId,
                             @RequestParam Long tournamentId,
                             RedirectAttributes ra) {
        try {
            playoffService.removeTeam(teamId);
            ra.addFlashAttribute("success", "Equipo eliminado");
        } catch (Exception e) {
            log.error("Error deleting team {}: {}", teamId, e.getMessage());
            ra.addFlashAttribute("error", "Error al eliminar: " + e.getMessage());
        }
        return "redirect:/tournaments/team-playoff/admin/" + tournamentId;
    }

    // ══════════════════════════════════════════════════════════════════════
    // ADMIN — ИНИЦИАЛИЗАЦИЯ ФАЗ
    // ══════════════════════════════════════════════════════════════════════

    @PostMapping("/admin/{tournamentId}/import-registrations")
    public String importRegistrations(@PathVariable Long tournamentId, RedirectAttributes ra) {
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
    public String initQualification(@PathVariable Long tournamentId,
                                    @RequestParam(defaultValue = "2") int courts,
                                    RedirectAttributes ra) {
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
    public String initPlayoff(@PathVariable Long tournamentId, RedirectAttributes ra) {
        try {
            playoffService.initPlayoff(tournamentId);
            ra.addFlashAttribute("success", "Playoff inicializado");
        } catch (Exception e) {
            log.error("Error init playoff {}: {}", tournamentId, e.getMessage());
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/tournaments/team-playoff/admin/" + tournamentId;
    }

    // ══════════════════════════════════════════════════════════════════════
    // ADMIN — ВВОД РЕЗУЛЬТАТОВ
    // ══════════════════════════════════════════════════════════════════════

    @PostMapping("/admin/qual-matches/{matchId}/result")
    public String submitQualResult(@PathVariable Long matchId,
                                   @RequestParam int team1Games,
                                   @RequestParam int team2Games,
                                   @RequestParam Long tournamentId,
                                   RedirectAttributes ra) {
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
    public String submitPlayoffResult(@PathVariable Long matchId,
                                      @RequestParam int team1Games,
                                      @RequestParam int team2Games,
                                      @RequestParam Long tournamentId,
                                      RedirectAttributes ra) {
        try {
            playoffService.submitPlayoffResult(matchId, team1Games, team2Games);
            ra.addFlashAttribute("success", "Resultado guardado");
        } catch (InvalidStateException e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error submitting playoff result {}: {}", matchId, e.getMessage());
            ra.addFlashAttribute("error", "Error al guardar: " + e.getMessage());
        }
        return "redirect:/tournaments/team-playoff/admin/" + tournamentId;
    }

    // ══════════════════════════════════════════════════════════════════════
    // REST API
    // ══════════════════════════════════════════════════════════════════════

    @PostMapping("/api/qual-matches/{matchId}/result")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitQualResultApi(
            @PathVariable Long matchId,
            @RequestBody Map<String, Integer> body) {
        Map<String, Object> result = new HashMap<>();
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
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitPlayoffResultApi(
            @PathVariable Long matchId,
            @RequestBody Map<String, Integer> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            int g1 = body.getOrDefault("team1Games", 0);
            int g2 = body.getOrDefault("team2Games", 0);
            playoffService.submitPlayoffResult(matchId, g1, g2);
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

    // ══════════════════════════════════════════════════════════════════════
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ══════════════════════════════════════════════════════════════════════

    private List<AmericanoRoundDto> buildRoundDtos(List<AmericanoRound> rounds) {
        return rounds.stream().map(round -> {
            List<AmericanoMatch> matches = matchRepository.findByRoundId(round.getId());

            AmericanoRoundDto dto = new AmericanoRoundDto();
            dto.setId(round.getId());
            dto.setTournamentId(round.getTournament().getId());
            dto.setRoundNumber(round.getRoundNumber());
            dto.setStatus(AmericanoRoundStatus.valueOf(round.getStatus().name()));
            dto.setCourts(round.getCourts());
            dto.setPointsPerMatch(round.getPointsPerMatch());
            dto.setNote(round.getNote());
            dto.setStartedAt(round.getStartedAt());
            dto.setCompletedAt(round.getCompletedAt());
            dto.setTotalMatches(matches.size());
            dto.setCompletedMatches(
                    (int) matches.stream().filter(AmericanoMatch::isCompleted).count());
            dto.setMatches(matches.stream().map(this::toMatchDto).toList());
            return dto;
        }).toList();
    }

    private AmericanoMatchDto toMatchDto(AmericanoMatch m) {
        AmericanoMatchDto dto = new AmericanoMatchDto();
        dto.setId(m.getId());
        dto.setMatchNumber(m.getMatchNumber());
        dto.setCourtNumber(m.getCourtNumber());
        dto.setStatus(AmericanoRoundStatus.valueOf(m.getStatus().name()));
        dto.setIsCompleted(m.isCompleted());
        dto.setTeam1Score(m.getTeam1Score());
        dto.setTeam2Score(m.getTeam2Score());
        dto.setNote(m.getNote());
        dto.setTournamentId(m.getTournament().getId());

        // Игроки
        if (m.getTeam1Player1() != null) {
            dto.setTeam1Player1Id(m.getTeam1Player1().getId());
            dto.setTeam1Player1Name(
                    m.getTeam1Player1().getNombre() + " " + m.getTeam1Player1().getApellido());
        }
        if (m.getTeam1Player2() != null) {
            dto.setTeam1Player2Id(m.getTeam1Player2().getId());
            dto.setTeam1Player2Name(
                    m.getTeam1Player2().getNombre() + " " + m.getTeam1Player2().getApellido());
        }
        if (m.getTeam2Player1() != null) {
            dto.setTeam2Player1Id(m.getTeam2Player1().getId());
            dto.setTeam2Player1Name(
                    m.getTeam2Player1().getNombre() + " " + m.getTeam2Player1().getApellido());
        }
        if (m.getTeam2Player2() != null) {
            dto.setTeam2Player2Id(m.getTeam2Player2().getId());
            dto.setTeam2Player2Name(
                    m.getTeam2Player2().getNombre() + " " + m.getTeam2Player2().getApellido());
        }

        // Геймы (для AMERICANO_TEAMS)
        dto.setTeam1Games(m.getTeam1Games());
        dto.setTeam2Games(m.getTeam2Games());
        if (m.getPlayoffStage() != null) {
            dto.setPlayoffStage(m.getPlayoffStage().name());
        }

        return dto;
    }
}
