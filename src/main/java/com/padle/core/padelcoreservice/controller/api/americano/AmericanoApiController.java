package com.padle.core.padelcoreservice.controller.api.americano;

import com.padle.core.padelcoreservice.dto.TournamentRegistrationDto;
import com.padle.core.padelcoreservice.dto.americano.*;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.americano.AmericanoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<TournamentRegistrationDto> registerForAmericano(
            @PathVariable Long tournamentId,
            @PathVariable Long playerId, @AuthenticationPrincipal Owner currentOwner) {
        log.info("API: Register player {} for Americano tournament {}", playerId, tournamentId);
        return ResponseEntity.ok(americanoService.registerForAmericano(tournamentId, playerId, currentOwner));
    }

    @DeleteMapping("/{tournamentId}/cancel/{playerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<Void> cancelRegistration(
            @PathVariable Long tournamentId,
            @PathVariable Long playerId,
            @RequestParam(required = false) String reason, @AuthenticationPrincipal Owner currentOwner) {
        log.info("API: Cancel registration for player {} in tournament {}", playerId, tournamentId);
        americanoService.cancelRegistration(tournamentId, playerId, reason, currentOwner);
        return ResponseEntity.ok().build();
    }

    // ==================== ИНИЦИАЛИЗАЦИЯ ====================

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
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<AmericanoRoundDto> startRound(@PathVariable Long roundId, @AuthenticationPrincipal Owner currentOwner) {
        log.info("API: Start round {}", roundId);
        return ResponseEntity.ok(americanoService.startRound(roundId, currentOwner));
    }

    @PostMapping("/rounds/{roundId}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<AmericanoRoundDto> completeRound(@PathVariable Long roundId, @AuthenticationPrincipal Owner currentOwner) {
        log.info("API: Complete round {}", roundId);
        return ResponseEntity.ok(americanoService.completeRound(roundId, currentOwner));
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
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<AmericanoMatchDto> submitMatchResult(
            @PathVariable Long matchId,
            @Valid @RequestBody AmericanoMatchResultDto resultDto, @AuthenticationPrincipal Owner currentOwner) {
        resultDto.setMatchId(matchId);
        log.info("API: Submit result for match {}", matchId);
        return ResponseEntity.ok(americanoService.submitMatchResult(resultDto, currentOwner));
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
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<Void> dropOutPlayer(
            @PathVariable Long tournamentId,
            @PathVariable Long playerId,
            @Valid @RequestBody AmericanoDropoutDto dropoutDto, @AuthenticationPrincipal Owner currentOwner) {

        dropoutDto.setPlayerId(playerId);
        log.info("API: Player {} drops out from tournament {}", playerId, tournamentId);
        americanoService.dropOutPlayer(tournamentId, dropoutDto, currentOwner);
        return ResponseEntity.ok().build();
    }

    // ==================== ЗАВЕРШЕНИЕ ТУРНИРА ====================

    @PostMapping("/{tournamentId}/finish")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<AmericanoRankingDto> finishTournament(
            @PathVariable Long tournamentId,
            @RequestParam(defaultValue = "score") String sortBy,
            @RequestParam(defaultValue = "false") boolean ascending, @AuthenticationPrincipal Owner currentOwner) {

        log.info("API: Finish Americano tournament {} with criteria: sortBy={}, ascending={}",
                tournamentId, sortBy, ascending);

        RankingCriteria criteria = RankingCriteria.builder()
                .sortBy(sortBy)
                .ascending(ascending)
                .build();

        return ResponseEntity.ok(americanoService.finishTournament(tournamentId, criteria, currentOwner));
    }

    @PutMapping("/rounds/{roundId}/points-limit")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'ORGANIZER')")
    public ResponseEntity<?> updateRoundPointsLimit(
            @PathVariable Long roundId,
            @RequestBody Map<String, Integer> request, @AuthenticationPrincipal Owner currentOwner) {

        Integer newLimit = request.get("pointsPerMatch");
        if (newLimit == null || (newLimit != 15 && newLimit != 21 && newLimit != 24 && newLimit != 32 && newLimit != 40)) {
            return ResponseEntity.badRequest().body("Invalid points limit");
        }

        try {
            americanoService.updateRoundPointsLimit(roundId, newLimit, currentOwner);
            return ResponseEntity.ok().build();
        } catch (InvalidStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Error updating points limit: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Error updating points limit");
        }
    }
}