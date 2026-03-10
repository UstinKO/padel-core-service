package com.padle.core.padelcoreservice.controller.test;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.service.TournamentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/test/tournaments")
@RequiredArgsConstructor
public class TestTournamentController {

    private final TournamentService tournamentService;
    private final DataSource dataSource;

    /**
     * Главная страница со списком всех турниров
     */
    @GetMapping
    public String listTournaments(Model model) {
        List<TournamentDto> tournaments = tournamentService.getAllTournaments();

        // Для каждого турнира получаем статистику регистраций
        Map<Long, Map<String, Object>> statsMap = new HashMap<>();

        try (Connection conn = dataSource.getConnection()) {
            for (TournamentDto tournament : tournaments) {
                Map<String, Object> stats = getTournamentStats(conn, tournament.getId());
                statsMap.put(tournament.getId(), stats);
            }
        } catch (Exception e) {
            log.error("Ошибка при получении статистики турниров", e);
        }

        model.addAttribute("tournaments", tournaments);
        model.addAttribute("statsMap", statsMap);
        model.addAttribute("now", LocalDateTime.now());

        return "test/tournaments";
    }

    /**
     * Массовая регистрация тестовых игроков на турнир
     */
    @PostMapping("/{tournamentId}/register-test-players")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> registerTestPlayers(
            @PathVariable Long tournamentId,
            @RequestParam(defaultValue = "25") int count,
            @RequestParam(defaultValue = "1") int startId) {

        log.info("Тестовая регистрация: турнир={}, игроки с {} по {}",
                tournamentId, startId, startId + count - 1);

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> registeredPlayers = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();

        int successCount = 0;
        int skipCount = 0;

        try (Connection conn = dataSource.getConnection()) {
            // Проверяем существование турнира
            TournamentDto tournament = tournamentService.getTournamentById(tournamentId)
                    .orElse(null);

            if (tournament == null) {
                result.put("success", false);
                result.put("message", "Турнир с ID " + tournamentId + " не найден");
                return ResponseEntity.badRequest().body(result);
            }

            result.put("tournamentInfo", Map.of(
                    "id", tournament.getId(),
                    "name", tournament.getNombre(),
                    "category", tournament.getCategoriaNivel(),
                    "maxPlayers", tournament.getCupoMax()
            ));

            // Регистрируем игроков
            for (int playerId = startId; playerId < startId + count; playerId++) {
                try {
                    Map<String, Object> regResult = registerPlayer(conn, tournamentId, (long) playerId);

                    if ((boolean) regResult.get("success")) {
                        successCount++;
                        registeredPlayers.add(regResult);
                        log.debug("✅ Зарегистрирован игрок {}", playerId);
                    } else {
                        if ("already_registered".equals(regResult.get("reason"))) {
                            skipCount++;
                            log.debug("⏭️ Игрок {} уже зарегистрирован", playerId);
                        } else {
                            errors.add(regResult);
                            log.debug("❌ Ошибка игрока {}: {}", playerId, regResult.get("error"));
                        }
                    }
                } catch (Exception e) {
                    log.error("Ошибка при регистрации игрока {}: {}", playerId, e.getMessage());

                    Map<String, Object> errorInfo = new HashMap<>();
                    errorInfo.put("playerId", playerId);
                    errorInfo.put("error", e.getMessage());
                    errors.add(errorInfo);
                }
            }

            // Получаем обновленную статистику
            Map<String, Object> stats = getTournamentStats(conn, tournamentId);

            result.put("success", true);
            result.put("message", "Тестовая регистрация завершена");
            result.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            result.put("tournamentId", tournamentId);
            result.put("requestedCount", count);
            result.put("successfullyRegistered", successCount);
            result.put("alreadyRegistered", skipCount);
            result.put("errors", errors.size());
            result.put("registeredPlayers", registeredPlayers);
            result.put("errorDetails", errors);
            result.put("tournamentStats", stats);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Критическая ошибка", e);
            result.put("success", false);
            result.put("message", "Ошибка: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * Очистка всех тестовых регистраций с турнира
     */
    @PostMapping("/{tournamentId}/clear-test-players")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearTestPlayers(
            @PathVariable Long tournamentId,
            @RequestParam(defaultValue = "50") int maxPlayerId) {

        Map<String, Object> result = new HashMap<>();

        String sql = "DELETE FROM tournament_registrations_db " +
                "WHERE tournament_id = ? AND player_id <= ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, tournamentId);
            ps.setInt(2, maxPlayerId);

            int deleted = ps.executeUpdate();

            // Получаем обновленную статистику
            Map<String, Object> stats = getTournamentStats(conn, tournamentId);

            result.put("success", true);
            result.put("deletedCount", deleted);
            result.put("message", "Удалено " + deleted + " тестовых регистраций");
            result.put("tournamentStats", stats);

        } catch (Exception e) {
            log.error("Ошибка при очистке", e);
            result.put("success", false);
            result.put("message", "Ошибка: " + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Получение актуальной статистики турнира
     */
    @GetMapping("/{tournamentId}/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStats(@PathVariable Long tournamentId) {
        try (Connection conn = dataSource.getConnection()) {
            Map<String, Object> stats = getTournamentStats(conn, tournamentId);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Ошибка при получении статистики", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Прямая SQL-регистрация игрока (как в твоем PS1 скрипте)
     */
    private Map<String, Object> registerPlayer(Connection conn, Long tournamentId, Long playerId) {
        Map<String, Object> result = new HashMap<>();
        result.put("playerId", playerId);

        try {
            // Проверяем, не зарегистрирован ли уже
            String checkSql = "SELECT COUNT(*) FROM tournament_registrations_db " +
                    "WHERE tournament_id = ? AND player_id = ?";

            try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                checkPs.setLong(1, tournamentId);
                checkPs.setLong(2, playerId);
                ResultSet rs = checkPs.executeQuery();

                if (rs.next() && rs.getInt(1) > 0) {
                    result.put("success", false);
                    result.put("reason", "already_registered");
                    result.put("message", "Игрок уже зарегистрирован");
                    return result;
                }
            }

            // Получаем следующий position (как в твоем PS1 скрипте)
            String maxPosSql = "SELECT COALESCE(MAX(position), 0) FROM tournament_registrations_db " +
                    "WHERE tournament_id = ?";
            int nextPosition = 1;

            try (PreparedStatement maxPs = conn.prepareStatement(maxPosSql)) {
                maxPs.setLong(1, tournamentId);
                ResultSet rs = maxPs.executeQuery();
                if (rs.next()) {
                    nextPosition = rs.getInt(1) + 1;
                }
            }

            // Регистрируем (прямая вставка как в PS1)
            String insertSql = "INSERT INTO tournament_registrations_db " +
                    "(tournament_id, player_id, registration_date, status, position, is_active) " +
                    "VALUES (?, ?, ?, 'CONFIRMED', ?, true)";

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setLong(1, tournamentId);
                ps.setLong(2, playerId);
                ps.setObject(3, LocalDateTime.now());
                ps.setInt(4, nextPosition);

                int inserted = ps.executeUpdate();

                result.put("success", inserted > 0);
                result.put("status", "REGISTERED");
                result.put("position", nextPosition);
                result.put("timestamp", LocalDateTime.now().toString());
            }

        } catch (Exception e) {
            log.error("Ошибка регистрации игрока {}", playerId, e);
            result.put("success", false);
            result.put("reason", "exception");
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Получение статистики турнира
     */
    private Map<String, Object> getTournamentStats(Connection conn, Long tournamentId) {
        Map<String, Object> stats = new HashMap<>();

        String sql = "SELECT " +
                "COUNT(*) as total, " +
                "SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END) as confirmed, " +
                "MIN(position) as min_pos, " +
                "MAX(position) as max_pos " +
                "FROM tournament_registrations_db " +
                "WHERE tournament_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, tournamentId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                stats.put("totalRegistrations", rs.getInt("total"));
                stats.put("confirmed", rs.getInt("confirmed"));
                stats.put("minPosition", rs.getInt("min_pos"));
                stats.put("maxPosition", rs.getInt("max_pos"));

                // Получаем информацию о турнире для лимита
                TournamentDto tournament = tournamentService.getTournamentById(tournamentId).orElse(null);
                if (tournament != null) {
                    stats.put("maxAllowed", tournament.getCupoMax());
                    stats.put("available", tournament.getCupoMax() - rs.getInt("confirmed"));
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при получении статистики", e);
        }

        return stats;
    }
}