package com.padle.core.padelcoreservice.controller.view.americano;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.dto.americano.*;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.service.americano.AmericanoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/tournaments/americano")
@RequiredArgsConstructor
@Slf4j
public class AmericanoViewController {

    private final AmericanoService americanoService;
    private final TournamentService tournamentService;

    // ==================== ОСНОВНАЯ СТРАНИЦА ====================

    @GetMapping("/{tournamentId}")
    public String viewTournament(
            @PathVariable Long tournamentId,
            @RequestParam(required = false, defaultValue = "score") String sortBy,
            @RequestParam(required = false, defaultValue = "false") boolean ascending,
            Model model,
            Authentication authentication) {

        TournamentDto tournament = tournamentService.getActiveTournamentById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

        boolean isInitialized = americanoService.isInitialized(tournamentId);
        int activePlayers = americanoService.getActivePlayersCount(tournamentId);

        model.addAttribute("tournament", tournament);
        model.addAttribute("isInitialized", isInitialized);
        model.addAttribute("activePlayers", activePlayers);
        model.addAttribute("isAdmin", authentication != null &&
                authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("ascending", ascending);

        // ДОБАВИТЬ: передаем информацию об авторизованном игроке
        model.addAttribute("isAuthenticated", authentication != null);
        if (authentication != null && authentication.getPrincipal() instanceof com.padle.core.padelcoreservice.model.PlayerPadel) {
            model.addAttribute("player", (com.padle.core.padelcoreservice.model.PlayerPadel) authentication.getPrincipal());
        } else if (authentication != null && authentication.getPrincipal() instanceof com.padle.core.padelcoreservice.model.Owner) {
            // Если это владелец клуба, тоже показываем как авторизованного
            model.addAttribute("player", null);
        }

        if (isInitialized) {
            RankingCriteria criteria = RankingCriteria.builder()
                    .sortBy(sortBy)
                    .ascending(ascending)
                    .build();

            AmericanoRankingDto ranking = americanoService.getRankingWithDetails(tournamentId, criteria);
            List<AmericanoRoundDto> rounds = americanoService.getRounds(tournamentId);

            model.addAttribute("ranking", ranking);
            model.addAttribute("rounds", rounds);
        }

        return "tournaments/americano/view";
    }

    // ==================== РЕГИСТРАЦИЯ ====================

    @GetMapping("/{tournamentId}/register")
    public String showRegisterForm(
            @PathVariable Long tournamentId,
            Model model,
            RedirectAttributes redirectAttributes,
            Authentication authentication) {

        if (authentication == null) {
            return "redirect:/login";
        }

        TournamentDto tournament = tournamentService.getActiveTournamentById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

        if (tournament.getTipo() != com.padle.core.padelcoreservice.model.enums.TournamentType.AMERICANO) {
            redirectAttributes.addFlashAttribute("error", "Este no es un torneo de tipo Americano");
            return "redirect:/tournaments/" + tournamentId;
        }

        if (americanoService.isInitialized(tournamentId)) {
            redirectAttributes.addFlashAttribute("error", "El torneo ya ha comenzado. No es posible registrarse.");
            return "redirect:/tournaments/americano/" + tournamentId;
        }

        model.addAttribute("tournament", tournament);
        model.addAttribute("playerId",
                ((com.padle.core.padelcoreservice.model.PlayerPadel) authentication.getPrincipal()).getId());

        return "tournaments/americano/register";
    }

    @PostMapping("/{tournamentId}/register")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    public String registerForTournament(
            @PathVariable Long tournamentId,
            @RequestParam Long playerId,
            RedirectAttributes redirectAttributes, @AuthenticationPrincipal Owner currentOwner) {

        try {
            TournamentRegistrationDto registration = americanoService.registerForAmericano(tournamentId, playerId, currentOwner);

            String status = registration.getStatus().name();
            if ("CONFIRMED".equals(status)) {
                redirectAttributes.addFlashAttribute("success", "¡Registro confirmado! Tu posición es: " + registration.getPosition());
            } else {
                redirectAttributes.addFlashAttribute("info", "Has sido agregado a la lista de espera. Posición: " + registration.getWaitlistPosition());
            }
        } catch (Exception e) {
            log.error("Error registering for tournament: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error al registrar: " + e.getMessage());
        }

        return "redirect:/tournaments/americano/" + tournamentId;
    }

    @PostMapping("/{tournamentId}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    public String cancelRegistration(
            @PathVariable Long tournamentId,
            @RequestParam Long playerId,
            @RequestParam(required = false) String reason,
            RedirectAttributes redirectAttributes, @AuthenticationPrincipal Owner currentOwner) {

        try {
            americanoService.cancelRegistration(tournamentId, playerId, reason, currentOwner);
            redirectAttributes.addFlashAttribute("success", "Registro cancelado correctamente");
        } catch (Exception e) {
            log.error("Error cancelling registration: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error al cancelar: " + e.getMessage());
        }

        return "redirect:/tournaments/americano/" + tournamentId;
    }

    // ==================== ИНИЦИАЛИЗАЦИЯ ====================

    @GetMapping("/{tournamentId}/initialize")
    public String showInitializeForm(
            @PathVariable Long tournamentId,
            Model model,
            RedirectAttributes redirectAttributes) {

        TournamentDto tournament = tournamentService.getActiveTournamentById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

        if (tournament.getTipo() != com.padle.core.padelcoreservice.model.enums.TournamentType.AMERICANO) {
            redirectAttributes.addFlashAttribute("error", "Este torneo no es de tipo Americano");
            return "redirect:/tournaments/" + tournamentId;
        }

        if (americanoService.isInitialized(tournamentId)) {
            redirectAttributes.addFlashAttribute("info", "El torneo ya está inicializado");
            return "redirect:/tournaments/americano/" + tournamentId;
        }

        model.addAttribute("tournament", tournament);
        model.addAttribute("config", AmericanoConfigDto.builder()
                .pointsPerMatch(32)
                .courts(2)
                .totalRounds(5)
                .shuffleAlgorithm("balanced")
                .build());

        return "tournaments/americano/initialize";
    }

    @PostMapping("/{tournamentId}/initialize")
    public String initializeTournament(
            @PathVariable Long tournamentId,
            @ModelAttribute AmericanoConfigDto config,
            RedirectAttributes redirectAttributes) {

        try {
            americanoService.initializeAmericanoTournament(tournamentId, config);
            redirectAttributes.addFlashAttribute("success",
                    "Torneo Americano inicializado correctamente con " + config.getTotalRounds() + " rondas");
        } catch (Exception e) {
            log.error("Error initializing Americano tournament: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error al inicializar: " + e.getMessage());
        }

        return "redirect:/tournaments/americano/" + tournamentId;
    }

    /**
     * Предпросмотр раундов.
     * ИСПРАВЛЕНО: добавлен isInitialized в модель — шаблон preview-rounds.html
     * использует его чтобы показать "Ir al torneo en curso" вместо кнопки инициализации
     * если турнир уже запущен.
     */
    @PostMapping("/{tournamentId}/preview-rounds")
    public String previewRounds(
            @PathVariable Long tournamentId,
            @ModelAttribute AmericanoConfigDto config,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            TournamentDto tournament = tournamentService.getActiveTournamentById(tournamentId)
                    .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

            // ── ДОБАВЛЕНО: передаём статус инициализации ──────────────────────
            boolean isInitialized = americanoService.isInitialized(tournamentId);
            model.addAttribute("isInitialized", isInitialized);
            // ──────────────────────────────────────────────────────────────────

            List<AmericanoRoundDto> previewRounds = americanoService.previewRounds(tournamentId, config);

            int totalMatches = 0;
            for (AmericanoRoundDto round : previewRounds) {
                totalMatches += round.getMatches().size();
            }

            model.addAttribute("tournament", tournament);
            model.addAttribute("config", config);
            model.addAttribute("previewRounds", previewRounds);
            model.addAttribute("totalMatches", totalMatches);
            model.addAttribute("matchesPerCourt", config.getCourts());

            return "admin/americano/preview-rounds";
        } catch (Exception e) {
            log.error("Error previewing rounds: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error al previsualizar: " + e.getMessage());
            return "redirect:/tournaments/americano/" + tournamentId + "/initialize";
        }
    }

    // ==================== РАУНДЫ ====================

    @GetMapping("/{tournamentId}/rounds")
    public String viewRounds(
            @PathVariable Long tournamentId,
            Model model) {

        TournamentDto tournament = tournamentService.getActiveTournamentById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

        List<AmericanoRoundDto> rounds = americanoService.getRounds(tournamentId);

        model.addAttribute("tournament", tournament);
        model.addAttribute("rounds", rounds);

        return "tournaments/americano/rounds";
    }

    @GetMapping("/rounds/{roundId}")
    public String viewRound(
            @PathVariable Long roundId,
            Model model) {

        AmericanoRoundDto round = americanoService.getRound(roundId);
        TournamentDto tournament = tournamentService.getActiveTournamentById(round.getTournamentId())
                .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

        List<AmericanoMatchDto> matches = americanoService.getMatchesByRound(roundId);

        model.addAttribute("tournament", tournament);
        model.addAttribute("round", round);
        model.addAttribute("matches", matches);

        return "tournaments/americano/round";
    }

    @PostMapping("/rounds/{roundId}/start")
    public String startRound(
            @PathVariable Long roundId,
            RedirectAttributes redirectAttributes) {

        try {
            AmericanoRoundDto round = americanoService.startRound(roundId);
            redirectAttributes.addFlashAttribute("success", "Ronda " + round.getRoundNumber() + " iniciada");
        } catch (Exception e) {
            log.error("Error starting round: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error al iniciar ronda: " + e.getMessage());
        }

        return "redirect:/tournaments/americano/rounds/" + roundId;
    }

    @PostMapping("/rounds/{roundId}/complete")
    public String completeRound(
            @PathVariable Long roundId,
            RedirectAttributes redirectAttributes) {

        try {
            AmericanoRoundDto round = americanoService.completeRound(roundId);
            redirectAttributes.addFlashAttribute("success", "Ronda " + round.getRoundNumber() + " completada");
        } catch (Exception e) {
            log.error("Error completing round: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error al completar ronda: " + e.getMessage());
        }

        return "redirect:/tournaments/americano/rounds/" + roundId;
    }

    // ==================== МАТЧИ ====================

    @GetMapping("/matches/{matchId}")
    public String viewMatch(
            @PathVariable Long matchId,
            Model model) {

        AmericanoMatchDto match = americanoService.getMatch(matchId);
        TournamentDto tournament = tournamentService.getActiveTournamentById(match.getTournamentId())
                .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

        model.addAttribute("tournament", tournament);
        model.addAttribute("match", match);

        return "tournaments/americano/match";
    }

    @GetMapping("/matches/{matchId}/result")
    public String showResultForm(
            @PathVariable Long matchId,
            Model model,
            RedirectAttributes redirectAttributes) {

        AmericanoMatchDto match = americanoService.getMatch(matchId);

        if (match.getIsCompleted()) {
            redirectAttributes.addFlashAttribute("info", "Este partido ya tiene resultado");
            return "redirect:/tournaments/americano/matches/" + matchId;
        }

        model.addAttribute("match", match);
        model.addAttribute("resultDto", new AmericanoMatchResultDto());

        return "tournaments/americano/match-result";
    }

    @PostMapping("/matches/{matchId}/result")
    public String submitMatchResult(
            @PathVariable Long matchId,
            @ModelAttribute AmericanoMatchResultDto resultDto,
            RedirectAttributes redirectAttributes) {

        try {
            resultDto.setMatchId(matchId);
            AmericanoMatchDto match = americanoService.submitMatchResult(resultDto);
            redirectAttributes.addFlashAttribute("success", "Resultado guardado correctamente");
            return "redirect:/tournaments/americano/matches/" + matchId;
        } catch (Exception e) {
            log.error("Error submitting match result: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error al guardar resultado: " + e.getMessage());
            return "redirect:/tournaments/americano/matches/" + matchId + "/result";
        }
    }

    // ==================== РЕЙТИНГ ====================

    @GetMapping("/{tournamentId}/ranking")
    public String viewRanking(
            @PathVariable Long tournamentId,
            @RequestParam(required = false, defaultValue = "score") String sortBy,
            @RequestParam(required = false, defaultValue = "false") boolean ascending,
            Model model) {

        TournamentDto tournament = tournamentService.getActiveTournamentById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

        RankingCriteria criteria = RankingCriteria.builder()
                .sortBy(sortBy)
                .ascending(ascending)
                .build();

        AmericanoRankingDto ranking = americanoService.getRankingWithDetails(tournamentId, criteria);

        model.addAttribute("tournament", tournament);
        model.addAttribute("ranking", ranking);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("ascending", ascending);

        return "tournaments/americano/ranking";
    }

    // ==================== СТАТИСТИКА ИГРОКА ====================

    @GetMapping("/{tournamentId}/players/{playerId}")
    public String viewPlayerStats(
            @PathVariable Long tournamentId,
            @PathVariable Long playerId,
            Model model) {

        TournamentDto tournament = tournamentService.getActiveTournamentById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

        AmericanoPlayerDto playerStats = americanoService.getPlayerStats(tournamentId, playerId);
        List<AmericanoMatchDto> matches = americanoService.getPlayerMatches(tournamentId, playerId);

        model.addAttribute("tournament", tournament);
        model.addAttribute("playerStats", playerStats);
        model.addAttribute("matches", matches);

        return "tournaments/americano/player-stats";
    }

    // ==================== УПРАВЛЕНИЕ ИГРОКАМИ ====================

    @PostMapping("/{tournamentId}/players/{playerId}/dropout")
    public String dropOutPlayer(
            @PathVariable Long tournamentId,
            @PathVariable Long playerId,
            @RequestParam(required = false) String reason,
            RedirectAttributes redirectAttributes) {

        try {
            AmericanoDropoutDto dropoutDto = AmericanoDropoutDto.builder()
                    .playerId(playerId)
                    .reason(reason)
                    .build();

            americanoService.dropOutPlayer(tournamentId, dropoutDto);
            redirectAttributes.addFlashAttribute("success", "Jugador marcado como abandonado");
        } catch (Exception e) {
            log.error("Error dropping out player: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error: " + e.getMessage());
        }

        return "redirect:/tournaments/americano/" + tournamentId;
    }

    // ==================== ЗАВЕРШЕНИЕ ТУРНИРА ====================

    @PostMapping("/{tournamentId}/finish")
    public String finishTournament(
            @PathVariable Long tournamentId,
            @RequestParam(required = false, defaultValue = "score") String sortBy,
            @RequestParam(required = false, defaultValue = "false") boolean ascending,
            RedirectAttributes redirectAttributes) {

        try {
            RankingCriteria criteria = RankingCriteria.builder()
                    .sortBy(sortBy)
                    .ascending(ascending)
                    .build();

            AmericanoRankingDto ranking = americanoService.finishTournament(tournamentId, criteria);

            String winner = ranking.getRanking().get(0).getPlayerName() + " " +
                    ranking.getRanking().get(0).getPlayerLastName();

            redirectAttributes.addFlashAttribute("success",
                    "Torneo finalizado. Campeón: " + winner + " con " +
                            ("score".equals(sortBy)
                                    ? ranking.getRanking().get(0).getTotalScore() + " puntos"
                                    : ranking.getRanking().get(0).getMatchesWon() + " victorias"));
        } catch (Exception e) {
            log.error("Error finishing tournament: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Error al finalizar: " + e.getMessage());
        }

        return "redirect:/tournaments/americano/" + tournamentId;
    }

    // ==================== АДМИНСКИЕ МЕТОДЫ ====================

    @GetMapping("/admin/{tournamentId}/preview")
    public String showAdminPreviewForm(
            @PathVariable Long tournamentId,
            Model model,
            RedirectAttributes redirectAttributes) {

        TournamentDto tournament = tournamentService.getActiveTournamentById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

        if (tournament.getTipo() != com.padle.core.padelcoreservice.model.enums.TournamentType.AMERICANO) {
            redirectAttributes.addFlashAttribute("error", "Este torneo no es de tipo Americano");
            return "redirect:/admin/tournaments/" + tournamentId;
        }

        if (americanoService.isInitialized(tournamentId)) {
            redirectAttributes.addFlashAttribute("info", "El torneo ya está inicializado");
            return "redirect:/admin/tournaments/americano/" + tournamentId;
        }

        model.addAttribute("tournament", tournament);
        model.addAttribute("config", AmericanoConfigDto.builder()
                .pointsPerMatch(32)
                .courts(2)
                .totalRounds(5)
                .shuffleAlgorithm("balanced")
                .build());

        return "admin/americano/initialize";
    }

    /**
     * Инициализация из страницы предпросмотра (POST /admin/{id}/initialize).
     * ИСПРАВЛЕНО:
     *   1. Если турнир уже инициализирован — тихий редирект без ошибки.
     *   2. Если статус != CERRADO — закрываем регистрацию перед инициализацией
     *      (аналогично тому как делает initializeAmericano из детальной карточки).
     *      Без этого сервис бросал InvalidStateException и страница preview
     *      не показывала ошибку — кнопка просто не работала.
     */
    @PostMapping("/admin/{tournamentId}/initialize")
    public String initializeAdminTournament(
            @PathVariable Long tournamentId,
            @ModelAttribute AmericanoConfigDto config,
            RedirectAttributes redirectAttributes) {

        // Если уже инициализирован — просто переходим на страницу управления
        if (americanoService.isInitialized(tournamentId)) {
            log.info("Tournament {} already initialized, redirecting to management page", tournamentId);
            return "redirect:/tournaments/americano/admin/" + tournamentId;
        }

        try {
            log.info("=== INITIALIZING AMERICANO TOURNAMENT (from preview) ===");
            log.info("tournamentId: {}, config: {}", tournamentId, config);

            // ── ИСПРАВЛЕНО: закрываем регистрацию если статус не CERRADO ──────
            // Сервис требует CERRADO, иначе бросает InvalidStateException.
            // Детальная карточка делает это в initializeAmericano — здесь тоже нужно.
            TournamentDto tournament = tournamentService.getActiveTournamentById(tournamentId)
                    .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

            if (!tournament.getEstado().toString().equals("CERRADO")) {
                log.info("Closing registration for tournament {} before initialization (current status: {})",
                        tournamentId, tournament.getEstado());
                tournamentService.updateTournamentStatus(tournamentId,
                        com.padle.core.padelcoreservice.model.enums.TournamentStatus.CERRADO,
                        null);
            }
            // ──────────────────────────────────────────────────────────────────

            americanoService.initializeAmericanoTournament(tournamentId, config);

            log.info("Tournament {} initialized successfully — rounds: {}, activePlayers: {}",
                    tournamentId,
                    americanoService.getRounds(tournamentId).size(),
                    americanoService.getActivePlayersCount(tournamentId));

            redirectAttributes.addFlashAttribute("success",
                    "Torneo Americano inicializado correctamente con " + config.getTotalRounds() + " rondas");
        } catch (Exception e) {
            log.error("Error initializing Americano tournament {}: {}", tournamentId, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error al inicializar: " + e.getMessage());
        }

        return "redirect:/tournaments/americano/admin/" + tournamentId;
    }

    /**
     * Страница управления запущенным турниром.
     */
    @GetMapping("/admin/{tournamentId}")
    public String viewAdminTournament(
            @PathVariable Long tournamentId,
            @RequestParam(required = false, defaultValue = "score") String sortBy,
            @RequestParam(required = false, defaultValue = "false") boolean ascending,
            Model model) {

        TournamentDto tournament = tournamentService.getActiveTournamentById(tournamentId)
                .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

        boolean isInitialized = americanoService.isInitialized(tournamentId);
        int activePlayers     = americanoService.getActivePlayersCount(tournamentId);

        log.info("=== VIEW ADMIN TOURNAMENT === tournamentId: {}, isInitialized: {}, activePlayers: {}",
                tournamentId, isInitialized, activePlayers);

        model.addAttribute("tournament", tournament);
        model.addAttribute("isInitialized", isInitialized);
        model.addAttribute("activePlayers", activePlayers);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("ascending", ascending);

        if (isInitialized) {
            RankingCriteria criteria = RankingCriteria.builder()
                    .sortBy(sortBy)
                    .ascending(ascending)
                    .build();

            AmericanoRankingDto ranking = americanoService.getRankingWithDetails(tournamentId, criteria);
            List<AmericanoRoundDto> rounds = americanoService.getRounds(tournamentId);

            log.info("ranking size: {}, rounds size: {}",
                    ranking != null ? ranking.getRanking().size() : 0,
                    rounds != null ? rounds.size() : 0);

            model.addAttribute("ranking", ranking);
            model.addAttribute("rounds", rounds);
        } else {
            model.addAttribute("ranking", AmericanoRankingDto.builder()
                    .tournamentId(tournamentId)
                    .tournamentName(tournament.getNombre())
                    .totalRounds(0)
                    .completedRounds(0)
                    .ranking(List.of())
                    .build());
            model.addAttribute("rounds", List.of());
        }

        return "admin/americano/tournament";
    }

    /**
     * Инициализация из детальной карточки турнира (POST /initialize с tournamentId в теле).
     * ИСПРАВЛЕНО: если турнир уже инициализирован — редирект без ошибки.
     */
    @PostMapping("/initialize")
    public String initializeAmericano(
            AmericanoConfigDto config,
            RedirectAttributes redirectAttributes) {

        Long tournamentId = config.getTournamentId();

        // ── ДОБАВЛЕНО: проверка до попытки инициализации ──────────────────
        if (americanoService.isInitialized(tournamentId)) {
            log.info("Tournament {} already initialized, redirecting to management page", tournamentId);
            return "redirect:/tournaments/americano/admin/" + tournamentId;
        }
        // ──────────────────────────────────────────────────────────────────

        try {
            log.info("=== INITIALIZE AMERICANO FROM ADMIN === tournamentId: {}, config: {}",
                    tournamentId, config);

            TournamentDto tournament = tournamentService.getActiveTournamentById(tournamentId)
                    .orElseThrow(() -> new IllegalArgumentException("Torneo no encontrado"));

            if (!tournament.getEstado().toString().equals("CERRADO")) {
                log.info("Closing registration for tournament {} (current status: {})",
                        tournamentId, tournament.getEstado());
                tournamentService.updateTournamentStatus(tournamentId,
                        com.padle.core.padelcoreservice.model.enums.TournamentStatus.CERRADO,
                        null);
            }

            americanoService.initializeAmericanoTournament(tournamentId, config);

            redirectAttributes.addFlashAttribute("success",
                    "Torneo Americano inicializado correctamente con " + config.getTotalRounds() + " rondas");
        } catch (Exception e) {
            log.error("Error initializing Americano tournament: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Error al inicializar: " + e.getMessage());
            return "redirect:/admin/tournaments/" + tournamentId;
        }

        return "redirect:/tournaments/americano/admin/" + tournamentId;
    }
}