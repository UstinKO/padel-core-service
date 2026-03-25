package com.padle.core.padelcoreservice.controller.api.americano;

import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.dto.americano.*;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.americano.AmericanoRound;
import com.padle.core.padelcoreservice.repository.americano.AmericanoRoundRepository;
import com.padle.core.padelcoreservice.service.americano.AmericanoService;
import com.padle.core.padelcoreservice.service.TournamentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tournaments/americano")
@RequiredArgsConstructor
@Slf4j
public class AmericanoApiController {

    private final AmericanoService americanoService;

    // ==================== РЕГИСТРАЦИЯ ====================

    @PostMapping("/{tournamentId}/register/{playerId}")
    public ResponseEntity<TournamentRegistrationDto> registerForAmericano(
            @PathVariable Long tournamentId,
            @PathVariable Long playerId) {
        log.info("API: Register player {} for Americano tournament {}", playerId, tournamentId);
        return ResponseEntity.ok(americanoService.registerForAmericano(tournamentId, playerId));
    }

    @DeleteMapping("/{tournamentId}/cancel/{playerId}")
    public ResponseEntity<Void> cancelRegistration(
            @PathVariable Long tournamentId,
            @PathVariable Long playerId,
            @RequestParam(required = false) String reason) {
        log.info("API: Cancel registration for player {} in tournament {}", playerId, tournamentId);
        americanoService.cancelRegistration(tournamentId, playerId, reason);
        return ResponseEntity.ok().build();
    }

    // ==================== ИНИЦИАЛИЗАЦИЯ ====================

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



    @GetMapping("/{tournamentId}/initialized")
    public ResponseEntity<Boolean> isInitialized(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(americanoService.isInitialized(tournamentId));
    }

    // ==================== РАУНДЫ ====================

    @GetMapping("/{tournamentId}/rounds")
    public ResponseEntity<List<AmericanoRoundDto>> getRounds(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(americanoService.getRounds(tournamentId));
    }

    @GetMapping("/rounds/{roundId}")
    public ResponseEntity<AmericanoRoundDto> getRound(@PathVariable Long roundId) {
        return ResponseEntity.ok(americanoService.getRound(roundId));
    }

    @PostMapping("/rounds/{roundId}/start")
    @PreAuthorize("hasRole('ADMIN') or @securityService.isRoundOrganizer(#roundId)")
    public ResponseEntity<AmericanoRoundDto> startRound(@PathVariable Long roundId) {
        log.info("API: Start round {}", roundId);
        return ResponseEntity.ok(americanoService.startRound(roundId));
    }

    @PostMapping("/rounds/{roundId}/complete")
    @PreAuthorize("hasRole('ADMIN') or @securityService.isRoundOrganizer(#roundId)")
    public ResponseEntity<AmericanoRoundDto> completeRound(@PathVariable Long roundId) {
        log.info("API: Complete round {}", roundId);
        return ResponseEntity.ok(americanoService.completeRound(roundId));
    }

    // ==================== МАТЧИ ====================

    @GetMapping("/{tournamentId}/matches")
    public ResponseEntity<List<AmericanoMatchDto>> getMatches(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(americanoService.getMatches(tournamentId));
    }

    @GetMapping("/rounds/{roundId}/matches")
    public ResponseEntity<List<AmericanoMatchDto>> getMatchesByRound(@PathVariable Long roundId) {
        return ResponseEntity.ok(americanoService.getMatchesByRound(roundId));
    }

    @GetMapping("/matches/{matchId}")
    public ResponseEntity<AmericanoMatchDto> getMatch(@PathVariable Long matchId) {
        return ResponseEntity.ok(americanoService.getMatch(matchId));
    }

    @PostMapping("/matches/{matchId}/result")
    public ResponseEntity<AmericanoMatchDto> submitMatchResult(
            @PathVariable Long matchId,
            @Valid @RequestBody AmericanoMatchResultDto resultDto) {
        resultDto.setMatchId(matchId);
        log.info("API: Submit result for match {}", matchId);
        return ResponseEntity.ok(americanoService.submitMatchResult(resultDto));
    }

    @GetMapping("/{tournamentId}/players/{playerId}/matches")
    public ResponseEntity<List<AmericanoMatchDto>> getPlayerMatches(
            @PathVariable Long tournamentId,
            @PathVariable Long playerId) {
        return ResponseEntity.ok(americanoService.getPlayerMatches(tournamentId, playerId));
    }

    // ==================== РЕЙТИНГ И СТАТИСТИКА ====================

    @GetMapping("/{tournamentId}/ranking")
    public ResponseEntity<AmericanoRankingDto> getRanking(
            @PathVariable Long tournamentId,
            @RequestParam(defaultValue = "score") String sortBy,
            @RequestParam(defaultValue = "false") boolean ascending) {

        RankingCriteria criteria = RankingCriteria.builder()
                .sortBy(sortBy)
                .ascending(ascending)
                .build();

        return ResponseEntity.ok(americanoService.getRankingWithDetails(tournamentId, criteria));
    }

    @GetMapping("/{tournamentId}/ranking/simple")
    public ResponseEntity<List<AmericanoPlayerDto>> getSimpleRanking(
            @PathVariable Long tournamentId,
            @RequestParam(defaultValue = "score") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection) {

        return ResponseEntity.ok(americanoService.getRanking(tournamentId, sortBy, sortDirection));
    }

    @GetMapping("/{tournamentId}/players/{playerId}/stats")
    public ResponseEntity<AmericanoPlayerDto> getPlayerStats(
            @PathVariable Long tournamentId,
            @PathVariable Long playerId) {
        return ResponseEntity.ok(americanoService.getPlayerStats(tournamentId, playerId));
    }

    @GetMapping("/{tournamentId}/players/count")
    public ResponseEntity<Integer> getActivePlayersCount(@PathVariable Long tournamentId) {
        return ResponseEntity.ok(americanoService.getActivePlayersCount(tournamentId));
    }

    // ==================== УПРАВЛЕНИЕ ИГРОКАМИ ====================

    @PostMapping("/{tournamentId}/players/{playerId}/dropout")
    @PreAuthorize("hasRole('ADMIN') or @securityService.isTournamentOrganizer(#tournamentId)")
    public ResponseEntity<Void> dropOutPlayer(
            @PathVariable Long tournamentId,
            @PathVariable Long playerId,
            @Valid @RequestBody AmericanoDropoutDto dropoutDto) {

        dropoutDto.setPlayerId(playerId);
        log.info("API: Player {} drops out from tournament {}", playerId, tournamentId);
        americanoService.dropOutPlayer(tournamentId, dropoutDto);
        return ResponseEntity.ok().build();
    }

    // ==================== ЗАВЕРШЕНИЕ ТУРНИРА ====================

    @PostMapping("/{tournamentId}/finish")
    @PreAuthorize("hasRole('ADMIN') or @securityService.isTournamentOrganizer(#tournamentId)")
    public ResponseEntity<AmericanoRankingDto> finishTournament(
            @PathVariable Long tournamentId,
            @RequestParam(defaultValue = "score") String sortBy,
            @RequestParam(defaultValue = "false") boolean ascending) {

        log.info("API: Finish Americano tournament {} with criteria: sortBy={}, ascending={}",
                tournamentId, sortBy, ascending);

        RankingCriteria criteria = RankingCriteria.builder()
                .sortBy(sortBy)
                .ascending(ascending)
                .build();

        return ResponseEntity.ok(americanoService.finishTournament(tournamentId, criteria));
    }

    // ==================== ДОПОЛНИТЕЛЬНЫЕ МЕТОДЫ ====================

    @GetMapping("/{tournamentId}/preview-rounds")
    @PreAuthorize("hasRole('ADMIN') or @securityService.isTournamentOrganizer(#tournamentId)")
    public ResponseEntity<List<AmericanoRoundDto>> previewRounds(
            @PathVariable Long tournamentId,
            @Valid @RequestBody AmericanoConfigDto config) {

        log.info("API: Preview rounds for tournament {} with config: {}", tournamentId, config);
        // Можно добавить метод для предпросмотра без сохранения в БД
        return ResponseEntity.ok(americanoService.previewRounds(tournamentId, config));
    }

    @PutMapping("/rounds/{roundId}/points-limit")
    public ResponseEntity<?> updateRoundPointsLimit(
            @PathVariable Long roundId,
            @RequestBody Map<String, Integer> request) {

        Integer newLimit = request.get("pointsPerMatch");
        if (newLimit == null || (newLimit != 15 && newLimit != 21 && newLimit != 24 && newLimit != 32 && newLimit != 40)) {
            return ResponseEntity.badRequest().body("Invalid points limit");
        }

        try {
            americanoService.updateRoundPointsLimit(roundId, newLimit);
            return ResponseEntity.ok().build();
        } catch (InvalidStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Error updating points limit: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Error updating points limit");
        }
    }
}