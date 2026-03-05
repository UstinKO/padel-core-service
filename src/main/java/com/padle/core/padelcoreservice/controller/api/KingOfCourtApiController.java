package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.*;
import com.padle.core.padelcoreservice.model.TournamentKingOfCourt;
import com.padle.core.padelcoreservice.service.KingOfCourtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/king-of-court")
@RequiredArgsConstructor
@Slf4j
public class KingOfCourtApiController {

    private final KingOfCourtService kingOfCourtService;

    /**
     * Инициализация турнира "Король Корта"
     */
    @PostMapping("/tournaments/{tournamentId}/initialize")
    public ResponseEntity<TournamentKingOfCourt> initializeKingOfCourt(
            @PathVariable Long tournamentId,
            @Valid @RequestBody InitializeKingOfCourtRequest request) {

        log.info("Initializing King of Court for tournament: {} with maxCourts: {}, calibrationRounds: {}",
                tournamentId, request.getMaxCourts(), request.getCalibrationRounds());

        try {
            TournamentKingOfCourt king = kingOfCourtService.initializeTournament(
                    tournamentId,
                    request.getMaxCourts(),
                    request.getCalibrationRounds()
            );
            return ResponseEntity.ok(king);
        } catch (Exception e) {
            log.error("Error initializing King of Court", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Получение текущего состояния турнира
     */
    @GetMapping("/tournaments/{kingId}/state")
    public ResponseEntity<KingOfCourtStateDTO> getTournamentState(@PathVariable Long kingId) {
        log.debug("Getting state for King of Court: {}", kingId);
        KingOfCourtStateDTO state = kingOfCourtService.getCurrentState(kingId);
        return ResponseEntity.ok(state);
    }

    /**
     * Получение рейтинга
     */
    @GetMapping("/tournaments/{kingId}/ranking")
    public ResponseEntity<List<PlayerStatsDTO>> getRanking(@PathVariable Long kingId) {
        KingOfCourtStateDTO state = kingOfCourtService.getCurrentState(kingId);
        return ResponseEntity.ok(state.getRanking());
    }

    /**
     * Сохранение результата матча
     */
    @PostMapping("/matches/result")
    public ResponseEntity<?> saveMatchResult(@Valid @RequestBody MatchResultRequest request) {
        log.info("Saving match result: {}", request);

        kingOfCourtService.saveMatchResult(
                request.getRoundId(),
                request.getCourtNumber(),
                request.getWinners(),
                request.getLosers(),
                request.getWinnersScore(),
                request.getLosersScore()
        );

        return ResponseEntity.ok().build();
    }

    /**
     * Переход к следующему раунду
     */
    @PostMapping("/tournaments/{kingId}/next-round")
    public ResponseEntity<?> nextRound(@PathVariable Long kingId) {
        log.info("Moving to next round for tournament: {}", kingId);
        kingOfCourtService.nextRound(kingId);
        return ResponseEntity.ok().build();
    }

    /**
     * Завершение турнира
     */
    @PostMapping("/tournaments/{kingId}/finish")
    public ResponseEntity<?> finishTournament(@PathVariable Long kingId) {
        log.info("Finishing tournament: {}", kingId);
        kingOfCourtService.finishTournament(kingId);
        return ResponseEntity.ok().build();
    }

    /**
     * Обновление YouTube ссылки
     */
    @PostMapping("/tournaments/{kingId}/youtube")
    public ResponseEntity<?> updateYoutubeLink(@PathVariable Long kingId,
                                               @RequestParam String youtubeLink) {
        log.info("Updating YouTube link for tournament: {}", kingId);
        kingOfCourtService.updateYoutubeLink(kingId, youtubeLink);
        return ResponseEntity.ok().build();
    }

    /**
     * Получение истории игрока
     */
    @GetMapping("/tournaments/{kingId}/players/{playerId}/history")
    public ResponseEntity<List<MatchHistoryDTO>> getPlayerHistory(
            @PathVariable Long kingId,
            @PathVariable Long playerId) {

        log.debug("Getting history for player: {} in tournament: {}", playerId, kingId);
        List<MatchHistoryDTO> history = kingOfCourtService.getPlayerMatchHistory(kingId, playerId);
        return ResponseEntity.ok(history);
    }

    /**
     * Обновление результата матча
     */
    @PutMapping("/matches/result/{resultId}")
    public ResponseEntity<?> updateMatchResult(
            @PathVariable Long resultId,
            @Valid @RequestBody MatchResultRequest request) {

        log.info("Updating match result with id: {}", resultId);

        kingOfCourtService.updateMatchResult(
                resultId,
                request.getCourtNumber(),
                request.getWinners(),
                request.getLosers(),
                request.getWinnersScore(),
                request.getLosersScore()
        );

        return ResponseEntity.ok().build();
    }

    /**
     * DEBUG: Проверка игроков в турнире (исправлено)
     */
    @GetMapping("/debug/tournaments/{kingId}/check-players")
    public ResponseEntity<DebugInfo> debugCheckPlayers(@PathVariable Long kingId) {
        log.info("DEBUG: Checking players for King of Court: {}", kingId);

        try {
            KingOfCourtStateDTO state = kingOfCourtService.getCurrentState(kingId);
            List<PlayerStatsDTO> stats = kingOfCourtService.getPlayerStats(kingId);

            // Исправлено: getRawPlayerStats возвращает List<Object[]>
            List<Object[]> rawStatsArray = kingOfCourtService.getRawPlayerStats(kingId);

            // Конвертируем Object[] в List<Object> для совместимости с DTO
            List<Object> rawStats = rawStatsArray.stream()
                    .map(Arrays::asList)
                    .collect(Collectors.toList());

            DebugInfo info = new DebugInfo(
                    state,
                    stats,
                    rawStats,
                    String.format("Players in ranking: %d, Players in stats: %d, Raw stats: %d",
                            state.getRanking() != null ? state.getRanking().size() : 0,
                            stats != null ? stats.size() : 0,
                            rawStats != null ? rawStats.size() : 0)
            );

            return ResponseEntity.ok(info);
        } catch (Exception e) {
            log.error("Debug error", e);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Простой ping для проверки работоспособности
     */
    @GetMapping("/ping")
    public ResponseEntity<String> ping() {
        return ResponseEntity.ok("pong");
    }

    /**
     * DTO для запроса инициализации
     */
    public static class InitializeKingOfCourtRequest {
        @NotNull(message = "Max courts is required")
        @Min(value = 1, message = "Max courts must be at least 1")
        private Integer maxCourts;

        @NotNull(message = "Calibration rounds is required")
        @Min(value = 0, message = "Calibration rounds must be at least 0")
        private Integer calibrationRounds;

        public Integer getMaxCourts() {
            return maxCourts;
        }

        public void setMaxCourts(Integer maxCourts) {
            this.maxCourts = maxCourts;
        }

        public Integer getCalibrationRounds() {
            return calibrationRounds;
        }

        public void setCalibrationRounds(Integer calibrationRounds) {
            this.calibrationRounds = calibrationRounds;
        }
    }

    /**
     * DTO для отладочной информации (исправлено)
     */
    public record DebugInfo(
            KingOfCourtStateDTO state,
            List<PlayerStatsDTO> playerStats,
            List<Object> rawPlayerStats,
            String message
    ) {}
}