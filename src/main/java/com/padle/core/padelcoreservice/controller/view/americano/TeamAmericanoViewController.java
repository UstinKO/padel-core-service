package com.padle.core.padelcoreservice.controller.view.americano;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoMatchDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoRoundDto;
import com.padle.core.padelcoreservice.dto.americano.AmericanoTeamDto;
import com.padle.core.padelcoreservice.dto.americano.TeamAmericanoConfigDto;
import com.padle.core.padelcoreservice.dto.americano.TeamAmericanoRankingDto;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.service.americano.TeamAmericanoService;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.repository.americano.AmericanoMatchRepository;
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
@RequestMapping("/tournaments/team-americano")
@RequiredArgsConstructor
@Slf4j
public class TeamAmericanoViewController {

    private final TeamAmericanoService teamAmericanoService;
    private final TournamentService tournamentService;
    private final AmericanoRoundRepository roundRepository;
    private final AmericanoMatchRepository matchRepository;

    // ══════════════════════════════════════════════════════════════════════
    // ПУБЛИЧНАЯ СТРАНИЦА ТУРНИРА
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Публичная страница турнира — рейтинг команд и расписание матчей.
     */
    @GetMapping("/{tournamentId}")
    public String viewTournament(@PathVariable Long tournamentId,
                                 Model model,
                                 Authentication authentication) {

        TournamentDto tournament = tournamentService.getTournamentDtoById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

        boolean isInitialized = teamAmericanoService.isInitialized(tournamentId);

        PlayerPadel player = SecurityUtils.extractPlayer(
                authentication != null ? authentication.getPrincipal() : null);

        model.addAttribute("tournament", tournament);
        model.addAttribute("isInitialized", isInitialized);
        model.addAttribute("isAuthenticated", authentication != null);
        model.addAttribute("player", player);
        model.addAttribute("currentPlayerId", player != null ? player.getId() : null);

        if (isInitialized) {
            TeamAmericanoRankingDto ranking = teamAmericanoService.getRanking(tournamentId);
            List<AmericanoRoundDto> rounds  = getRoundsDto(tournamentId);
            model.addAttribute("ranking", ranking);
            model.addAttribute("rounds", rounds);
        }

        return "tournaments/team-americano/view";
    }

    /**
     * Страница рейтинга команд (публичная).
     */
    @GetMapping("/{tournamentId}/ranking")
    public String viewRanking(@PathVariable Long tournamentId, Model model) {

        TournamentDto tournament = tournamentService.getTournamentDtoById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

        TeamAmericanoRankingDto ranking = teamAmericanoService.getRanking(tournamentId);

        model.addAttribute("tournament", tournament);
        model.addAttribute("ranking", ranking);

        return "tournaments/team-americano/ranking";
    }

    // ══════════════════════════════════════════════════════════════════════
    // ИНИЦИАЛИЗАЦИЯ (ADMIN)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Инициализация из детальной карточки турнира (аналог /americano/initialize).
     * Принимает POST с tournamentId + конфигурацию.
     */
    @PostMapping("/initialize")
    public String initializeTeamAmericano(@ModelAttribute TeamAmericanoConfigDto config,
                                          @RequestParam Long tournamentId,
                                          RedirectAttributes redirectAttributes) {

        if (teamAmericanoService.isInitialized(tournamentId)) {
            log.info("Team Americano {} already initialized, redirecting", tournamentId);
            return "redirect:/tournaments/team-americano/admin/" + tournamentId;
        }

        try {
            log.info("Initializing Team Americano tournamentId={} courts={} points={}",
                    tournamentId, config.getCourts(), config.getPointsPerMatch());

            // Закрываем регистрацию если нужно
            TournamentDto tournament = tournamentService.getActiveTournamentById(tournamentId)
                    .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

            if (!tournament.getEstado().toString().equals("CERRADO")) {
                tournamentService.updateTournamentStatus(
                        tournamentId, TournamentStatus.CERRADO, null);
            }

            teamAmericanoService.initialize(tournamentId, config);
            redirectAttributes.addFlashAttribute("success",
                    "¡Team Americano inicializado! Todas las parejas tienen su fixture.");

        } catch (Exception e) {
            log.error("Error initializing Team Americano {}: {}", tournamentId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error al inicializar: " + e.getMessage());
            return "redirect:/admin/tournaments/" + tournamentId;
        }

        return "redirect:/tournaments/americano/admin/" + tournamentId;
    }

    /**
     * POST /tournaments/team-americano/preview
     * Превью Round Robin расписания без сохранения — аналог /americano/{id}/preview-rounds
     */
    @PostMapping("/preview")
    public String previewRounds(@RequestParam Long tournamentId,
                                @ModelAttribute TeamAmericanoConfigDto config,
                                Model model,
                                RedirectAttributes redirectAttributes) {
        try {
            TournamentDto tournament = tournamentService.getActiveTournamentById(tournamentId)
                    .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

            List<com.padle.core.padelcoreservice.dto.americano.AmericanoRoundDto> previewRounds =
                    teamAmericanoService.previewRounds(tournamentId, config);

            // Количество пар и матчей для отображения в конфиге
            int totalTeams  = previewRounds.isEmpty() ? 0
                    : previewRounds.get(0).getMatches().size() > 0
                    ? (int) Math.round((1 + Math.sqrt(1 + 8.0 * previewRounds.stream()
                    .mapToInt(r -> r.getMatches().size()).sum())) / 2)
                    : 0;
            int totalMatches = previewRounds.stream()
                    .mapToInt(r -> r.getMatches().size()).sum();

            model.addAttribute("tournament", tournament);
            model.addAttribute("config", config);
            model.addAttribute("previewRounds", previewRounds);
            model.addAttribute("totalTeams", tournament.getInscritosActuales() / 2);
            model.addAttribute("totalMatches", totalMatches);
            model.addAttribute("isTeamAmericanoInitialized",
                    teamAmericanoService.isInitialized(tournamentId));

            return "admin/americano/preview-double-rounds";

        } catch (Exception e) {
            log.error("Error previewing Team Americano rounds {}: {}", tournamentId, e.getMessage());
            redirectAttributes.addFlashAttribute("error",
                    "Error al previsualizar: " + e.getMessage());
            return "redirect:/admin/tournaments/" + tournamentId;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ADMIN — СТРАНИЦА УПРАВЛЕНИЯ ТУРНИРОМ
    // ══════════════════════════════════════════════════════════════════════

    @GetMapping("/admin/{tournamentId}")
    public String viewAdminTournament(@PathVariable Long tournamentId,
                                      Model model) {

        TournamentDto tournament = tournamentService.getActiveTournamentById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

        boolean isInitialized = teamAmericanoService.isInitialized(tournamentId);

        log.info("Team Americano admin view: tournamentId={}, isInitialized={}", tournamentId, isInitialized);

        model.addAttribute("tournament", tournament);
        model.addAttribute("isInitialized", isInitialized);

        if (isInitialized) {
            TeamAmericanoRankingDto ranking = teamAmericanoService.getRanking(tournamentId);
            List<AmericanoRoundDto> rounds  = getRoundsDto(tournamentId);
            List<AmericanoTeamDto>  teams   = teamAmericanoService.getTeams(tournamentId);

            model.addAttribute("ranking", ranking);
            model.addAttribute("rounds", rounds);
            model.addAttribute("teams", teams);
        } else {
            model.addAttribute("ranking", TeamAmericanoRankingDto.builder()
                    .tournamentId(tournamentId)
                    .tournamentName(tournament.getNombre())
                    .totalRounds(0).completedRounds(0).totalTeams(0)
                    .ranking(List.of()).build());
            model.addAttribute("rounds", List.of());
            model.addAttribute("teams", List.of());
        }

        return "admin/americano/tournament-double";
    }

    // ══════════════════════════════════════════════════════════════════════
    // МАТЧИ — ВВОД РЕЗУЛЬТАТОВ
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Страница ввода результата конкретного матча.
     */
    @GetMapping("/matches/{matchId}/result")
    public String showResultForm(@PathVariable Long matchId,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {

        AmericanoMatch match = matchRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));

        if (match.isCompleted()) {
            redirectAttributes.addFlashAttribute("info", "Este partido ya tiene resultado");
            return "redirect:/tournaments/team-americano/admin/"
                    + match.getTournament().getId();
        }

        TournamentDto tournament = tournamentService.getActiveTournamentById(
                match.getTournament().getId()).orElseThrow();

        model.addAttribute("tournament", tournament);
        model.addAttribute("match", match);
        model.addAttribute("pointsPerMatch", match.getRound().getPointsPerMatch());

        return "admin/americano/match-result";
    }

    /**
     * Сохранение результата матча (POST).
     */
    @PostMapping("/matches/{matchId}/result")
    public String submitMatchResult(@PathVariable Long matchId,
                                    @RequestParam int team1Score,
                                    @RequestParam int team2Score,
                                    RedirectAttributes redirectAttributes) {

        try {
            AmericanoMatch match = teamAmericanoService.submitMatchResult(matchId, team1Score, team2Score);
            redirectAttributes.addFlashAttribute("success", "Resultado guardado correctamente");
            return "redirect:/tournaments/team-americano/admin/"
                    + match.getTournament().getId();
        } catch (InvalidStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/tournaments/team-americano/matches/" + matchId + "/result";
        } catch (Exception e) {
            log.error("Error submitting match result matchId={}: {}", matchId, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error al guardar: " + e.getMessage());
            return "redirect:/tournaments/team-americano/matches/" + matchId + "/result";
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // ЗАВЕРШЕНИЕ ТУРНИРА
    // ══════════════════════════════════════════════════════════════════════

    @PostMapping("/admin/{tournamentId}/finish")
    public String finishTournament(@PathVariable Long tournamentId,
                                   RedirectAttributes redirectAttributes) {
        try {
            TeamAmericanoRankingDto ranking = teamAmericanoService.finishTournament(tournamentId);
            String winner = ranking.getRanking().isEmpty()
                    ? "—"
                    : ranking.getRanking().get(0).getDisplayName();
            redirectAttributes.addFlashAttribute("success",
                    "Torneo finalizado. Campeón: " + winner);
        } catch (Exception e) {
            log.error("Error finishing Team Americano {}: {}", tournamentId, e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error al finalizar: " + e.getMessage());
        }
        return "redirect:/tournaments/americano/admin/" + tournamentId;
    }

    // ══════════════════════════════════════════════════════════════════════
    // REST API (используется из JS на странице)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * API: сохранить результат матча (используется из JS без перезагрузки страницы).
     */
    @PostMapping("/api/matches/{matchId}/result")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitResultApi(@PathVariable Long matchId,
                                                               @RequestBody Map<String, Integer> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            int t1 = body.getOrDefault("team1Score", 0);
            int t2 = body.getOrDefault("team2Score", 0);
            teamAmericanoService.submitMatchResult(matchId, t1, t2);
            result.put("success", true);
            result.put("message", "Resultado guardado");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    /**
     * API: получить текущий рейтинг команд.
     */
    @GetMapping("/api/{tournamentId}/ranking")
    @ResponseBody
    public ResponseEntity<TeamAmericanoRankingDto> getRankingApi(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(teamAmericanoService.getRanking(tournamentId));
    }

    // ══════════════════════════════════════════════════════════════════════
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Собирает DTO для раундов с матчами (аналогично AmericanoService.getRounds).
     */
    private List<AmericanoRoundDto> getRoundsDto(Long tournamentId) {
        return roundRepository.findByTournamentIdOrderByRoundNumberAsc(tournamentId)
                .stream()
                .map(round -> {
                    List<AmericanoMatch> matches = matchRepository.findByRoundId(round.getId());

                    AmericanoRoundDto dto = new AmericanoRoundDto();
                    dto.setId(round.getId());
                    dto.setTournamentId(tournamentId);
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

                    // Матчи преобразуем в минимальный DTO для отображения
                    List<AmericanoMatchDto> matchDtos = matches.stream()
                            .map(this::toMatchDto)
                            .toList();
                    dto.setMatches(matchDtos);

                    return dto;
                })
                .toList();
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

        // Команда 1
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
        // Команда 2
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

        dto.setTournamentId(m.getTournament().getId());
        return dto;
    }
}