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
        int activeTournamentsCount = 0;
        int totalRegistrations = 0;
        int availablePlayersCount = 0;

        try (Connection conn = dataSource.getConnection()) {
            for (TournamentDto tournament : tournaments) {
                Map<String, Object> stats = getTournamentStats(conn, tournament.getId());
                statsMap.put(tournament.getId(), stats);

                totalRegistrations += (int) stats.getOrDefault("totalRegistrations", 0);

                if (List.of("REGISTRO_ABIERTO", "PUBLICADO").contains(tournament.getEstado())) {
                    activeTournamentsCount++;
                }
            }

            // Исправленный подсчет активных игроков
            String playerCountSql = "SELECT COUNT(*) FROM player_padel_db WHERE activo = true AND email_confirmado = true";
            try (PreparedStatement ps = conn.prepareStatement(playerCountSql);
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    availablePlayersCount = rs.getInt(1);
                }
            }

        } catch (Exception e) {
            log.error("Ошибка при получении статистики турниров", e);
        }

        model.addAttribute("tournaments", tournaments);
        model.addAttribute("statsMap", statsMap);
        model.addAttribute("now", LocalDateTime.now());
        model.addAttribute("activeTournamentsCount", activeTournamentsCount);
        model.addAttribute("totalRegistrations", totalRegistrations);
        model.addAttribute("availablePlayersCount", availablePlayersCount);

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
            TournamentDto tournament = tournamentService.getTournamentDtoById(tournamentId)
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
     * Статистика всех турниров одним запросом на одном соединении.
     * Страница test/tournaments раньше дергала /stats по числу карточек параллельно —
     * это исчерпывало пул HikariCP (см. HikariPool timeout в проде).
     */
    @GetMapping("/stats-all")
    @ResponseBody
    public ResponseEntity<Map<Long, Map<String, Object>>> getAllStats() {
        Map<Long, Map<String, Object>> statsMap = new HashMap<>();

        try (Connection conn = dataSource.getConnection()) {
            List<TournamentDto> tournaments = tournamentService.getAllTournaments();
            for (TournamentDto tournament : tournaments) {
                statsMap.put(tournament.getId(), getTournamentStats(conn, tournament.getId()));
            }
            return ResponseEntity.ok(statsMap);
        } catch (Exception e) {
            log.error("Ошибка при получении статистики всех турниров", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Получение списка всех игроков для регистрации на турнир
     */
    @GetMapping("/available-players")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAvailablePlayers(
            @RequestParam(required = false) Long tournamentId) {

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> players = new ArrayList<>();

        // Если tournamentId не передан, возвращаем пустой список
        if (tournamentId == null) {
            result.put("success", true);
            result.put("players", players);
            result.put("count", 0);
            result.put("message", "Не указан ID турнира");
            return ResponseEntity.ok(result);
        }

        // УПРОЩЕННЫЙ SQL: показываем ВСЕХ игроков, даже если они уже зарегистрированы
        // Убрали все условия WHERE, оставили только сортировку
        String sql = "SELECT p.id, p.nombre, p.apellido, p.email, p.telefono " +
                "FROM player_padel_db p " +
                "ORDER BY p.nombre, p.apellido";

        log.debug("Запрос ВСЕХ игроков для турнира ID: {} (без фильтрации)", tournamentId);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> player = new HashMap<>();
                    player.put("id", rs.getLong("id"));

                    // Собираем полное имя
                    String nombre = rs.getString("nombre");
                    String apellido = rs.getString("apellido");
                    String fullName = nombre + (apellido != null ? " " + apellido : "");

                    player.put("name", fullName);
                    player.put("email", rs.getString("email"));
                    player.put("phone", rs.getString("telefono") != null ? rs.getString("telefono") : "");
                    players.add(player);
                }
            }

            result.put("success", true);
            result.put("players", players);
            result.put("count", players.size());
            result.put("message", "Показаны все игроки из базы (включая уже зарегистрированных)");

            log.debug("Найдено всего игроков в базе: {}", players.size());

        } catch (Exception e) {
            log.error("Ошибка при получении списка игроков", e);
            result.put("success", false);
            result.put("message", "Ошибка: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{tournamentId}/register-real-players")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> registerRealPlayers(
            @PathVariable Long tournamentId,
            @RequestBody Map<String, Object> request) {

        log.info("Регистрация реальных игроков: турнир={}", tournamentId);

        Map<String, Object> result = new HashMap<>();

        List<Integer> playerIdsInt = (List<Integer>) request.get("playerIds");
        List<Long> playerIds = new ArrayList<>();
        for (Integer id : playerIdsInt) {
            playerIds.add(id.longValue());
        }

        log.info("Получены ID игроков: {}", playerIds);

        if (playerIds == null || playerIds.isEmpty()) {
            result.put("success", false);
            result.put("message", "Не указаны игроки для регистрации");
            return ResponseEntity.badRequest().body(result);
        }

        int successCount = 0;
        int waitlistCount = 0;
        int skipCount = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        List<Map<String, Object>> registered = new ArrayList<>();
        List<Map<String, Object>> waitlisted = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            TournamentDto tournament = tournamentService.getTournamentDtoById(tournamentId)
                    .orElse(null);

            if (tournament == null) {
                result.put("success", false);
                result.put("message", "Турнир с ID " + tournamentId + " не найден");
                return ResponseEntity.badRequest().body(result);
            }

            // Получаем текущую статистику
            Map<String, Object> currentStats = getTournamentStats(conn, tournamentId);
            int confirmed = (int) currentStats.getOrDefault("confirmed", 0);
            int available = tournament.getCupoMax() - confirmed;

            // Получаем текущий максимальный номер в листе ожидания
            String maxWaitlistSql = "SELECT COALESCE(MAX(waitlist_position), 0) FROM tournament_registrations_db " +
                    "WHERE tournament_id = ? AND waitlist_position IS NOT NULL";
            int nextWaitlistPosition = 1;
            try (PreparedStatement maxPs = conn.prepareStatement(maxWaitlistSql)) {
                maxPs.setLong(1, tournamentId);
                ResultSet rs = maxPs.executeQuery();
                if (rs.next()) {
                    nextWaitlistPosition = rs.getInt(1) + 1;
                }
            }

            log.info("Турнир: занято {}/{} мест, следующий номер в резерве: {}",
                    confirmed, tournament.getCupoMax(), nextWaitlistPosition);

            // Регистрируем выбранных игроков
            for (Long playerId : playerIds) {
                try {
                    // Проверяем, не зарегистрирован ли уже
                    String checkSql = "SELECT status, waitlist_position FROM tournament_registrations_db " +
                            "WHERE tournament_id = ? AND player_id = ?";
                    boolean alreadyRegistered = false;
                    try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                        checkPs.setLong(1, tournamentId);
                        checkPs.setLong(2, playerId);
                        ResultSet rs = checkPs.executeQuery();
                        if (rs.next()) {
                            alreadyRegistered = true;
                            String status = rs.getString("status");
                            if ("WAITLIST".equals(status)) {
                                log.debug("⏭️ Игрок {} уже в резерве на позиции {}",
                                        playerId, rs.getInt("waitlist_position"));
                            } else {
                                log.debug("⏭️ Игрок {} уже зарегистрирован", playerId);
                            }
                        }
                    }

                    if (alreadyRegistered) {
                        skipCount++;
                        continue;
                    }

                    // Определяем, попадает игрок в основной список или в резерв
                    if (confirmed < tournament.getCupoMax()) {
                        // Есть места - регистрируем в основной состав
                        Map<String, Object> regResult = registerPlayer(conn, tournamentId, playerId);
                        if ((boolean) regResult.get("success")) {
                            successCount++;
                            registered.add(regResult);
                            confirmed++; // Увеличиваем счетчик для следующего игрока
                            log.debug("✅ Зарегистрирован игрок {} в основной состав", playerId);
                        }
                    } else {
                        // Мест нет - добавляем в лист ожидания
                        Map<String, Object> waitlistResult = addToWaitlist(conn, tournamentId, playerId, nextWaitlistPosition++);
                        if ((boolean) waitlistResult.get("success")) {
                            waitlistCount++;
                            waitlisted.add(waitlistResult);
                            log.debug("⏳ Игрок {} добавлен в резерв на позицию {}",
                                    playerId, waitlistResult.get("waitlistPosition"));
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
            result.put("message", "Регистрация завершена");
            result.put("tournamentId", tournamentId);
            result.put("requestedCount", playerIds.size());
            result.put("successfullyRegistered", successCount);
            result.put("waitlistCount", waitlistCount);
            result.put("alreadyRegistered", skipCount);
            result.put("errors", errors.size());
            result.put("registeredPlayers", registered);
            result.put("waitlistedPlayers", waitlisted);
            result.put("errorDetails", errors);
            result.put("tournamentStats", stats);

            if (waitlistCount > 0) {
                result.put("warning", "Турнир заполнен. " + waitlistCount + " игроков добавлены в резерв.");
            }

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Критическая ошибка", e);
            result.put("success", false);
            result.put("message", "Ошибка: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * Получение списка зарегистрированных игроков турнира с группировкой по парам
     */
    @GetMapping("/{tournamentId}/players")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTournamentPlayers(@PathVariable Long tournamentId) {

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> mainPlayers = new ArrayList<>();
        List<Map<String, Object>> waitlistPlayers = new ArrayList<>();
        Map<Integer, List<Map<String, Object>>> pairs = new HashMap<>();

        // Получаем информацию о турнире
        TournamentDto tournament = tournamentService.getTournamentDtoById(tournamentId).orElse(null);
        boolean isDoubles = tournament != null && "DOBLES".equals(tournament.getModalidad().name());

        // Фильтруем: только активные регистрации + INNER JOIN гарантирует что игрок существует.
        // is_active = true исключает CANCELLED записи (дубли от повторных регистраций или деактивации).
        String sql = "SELECT tr.position, tr.status, tr.registration_date, tr.is_double_registration, " +
                "tr.waitlist_position, " +
                "p.id, p.nombre, p.apellido, p.email, p.telefono " +
                "FROM tournament_registrations_db tr " +
                "JOIN player_padel_db p ON tr.player_id = p.id " +
                "WHERE tr.tournament_id = ? AND tr.is_active = true " +
                "ORDER BY " +
                "CASE WHEN tr.status = 'WAITLIST' THEN 1 ELSE 0 END, " +
                "tr.position, tr.waitlist_position";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, tournamentId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> player = new HashMap<>();
                    player.put("id", rs.getLong("id"));

                    String nombre = rs.getString("nombre");
                    String apellido = rs.getString("apellido");
                    String fullName = nombre + (apellido != null ? " " + apellido : "");

                    player.put("name", fullName);
                    player.put("email", rs.getString("email"));
                    player.put("phone", rs.getString("telefono"));
                    player.put("position", rs.getObject("position"));
                    player.put("status", rs.getString("status"));
                    // ИСПРАВЛЕНО: используем правильное имя колонки
                    player.put("waitlistPosition", rs.getObject("waitlist_position"));
                    player.put("registrationDate", rs.getTimestamp("registration_date"));
                    player.put("isDoubleRegistration", rs.getBoolean("is_double_registration"));

                    if ("WAITLIST".equals(rs.getString("status"))) {
                        waitlistPlayers.add(player);
                    } else {
                        mainPlayers.add(player);

                        // Для парных турниров группируем по позициям
                        if (isDoubles && rs.getObject("position") != null) {
                            Integer position = rs.getInt("position");
                            if (!pairs.containsKey(position)) {
                                pairs.put(position, new ArrayList<>());
                            }
                            pairs.get(position).add(player);
                        }
                    }
                }
            }

            result.put("success", true);
            result.put("mainPlayers", mainPlayers);
            result.put("waitlistPlayers", waitlistPlayers);
            if (isDoubles && !pairs.isEmpty()) {
                result.put("pairs", pairs);
            }
            result.put("mainCount", mainPlayers.size());
            result.put("waitlistCount", waitlistPlayers.size());
            result.put("totalCount", mainPlayers.size() + waitlistPlayers.size());
            result.put("isDoubles", isDoubles);

            log.info("Найдено игроков: основная группа {}, резерв {}", mainPlayers.size(), waitlistPlayers.size());

        } catch (Exception e) {
            log.error("Ошибка при получении игроков турнира", e);
            result.put("success", false);
            result.put("message", "Ошибка: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/debug/registrations/{tournamentId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> debugRegistrations(@PathVariable Long tournamentId) {

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> registrations = new ArrayList<>();

        String sql = "SELECT tr.*, p.nombre, p.apellido, p.email " +
                "FROM tournament_registrations_db tr " +
                "JOIN player_padel_db p ON tr.player_id = p.id " +
                "WHERE tr.tournament_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, tournamentId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> reg = new HashMap<>();
                    reg.put("id", rs.getLong("id"));
                    reg.put("player_id", rs.getLong("player_id"));
                    reg.put("player_name", rs.getString("nombre") + " " + rs.getString("apellido"));
                    reg.put("status", rs.getString("status"));
                    reg.put("position", rs.getObject("position"));
                    reg.put("waitlist_position", rs.getObject("waitlist_position"));
                    reg.put("registration_date", rs.getTimestamp("registration_date"));
                    registrations.add(reg);
                }
            }

            result.put("success", true);
            result.put("registrations", registrations);
            result.put("count", registrations.size());

        } catch (Exception e) {
            log.error("Ошибка при отладке регистраций", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Регистрация пары на турнир (игроки выбирают себе пару сами)
     */
    @PostMapping("/{tournamentId}/register-pair-with-players")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> registerPairWithPlayers(
            @PathVariable Long tournamentId,
            @RequestBody Map<String, Object> request) {

        log.info("Регистрация пары на турнир: {}", tournamentId);

        Map<String, Object> result = new HashMap<>();
        List<Integer> playerIdsInt = (List<Integer>) request.get("playerIds");

        if (playerIdsInt == null || playerIdsInt.size() != 2) {
            result.put("success", false);
            result.put("message", "Для парного турнира нужно выбрать ровно двух игроков");
            return ResponseEntity.badRequest().body(result);
        }

        List<Long> playerIds = new ArrayList<>();
        for (Integer id : playerIdsInt) {
            playerIds.add(id.longValue());
        }

        log.info("Регистрируем пару с игроками: {}", playerIds);

        try (Connection conn = dataSource.getConnection()) {
            // Проверяем существование турнира
            TournamentDto tournament = tournamentService.getTournamentDtoById(tournamentId)
                    .orElse(null);

            if (tournament == null) {
                result.put("success", false);
                result.put("message", "Турнир с ID " + tournamentId + " не найден");
                return ResponseEntity.badRequest().body(result);
            }

            // Проверяем, что турнир парный
            if (!"DOBLES".equals(tournament.getModalidad().name())) {
                result.put("success", false);
                result.put("message", "Этот турнир не является парным");
                return ResponseEntity.badRequest().body(result);
            }

            // Проверяем свободные места
            Map<String, Object> currentStats = getTournamentStats(conn, tournamentId);
            int confirmed = (int) currentStats.getOrDefault("confirmed", 0);
            int available = tournament.getCupoMax() - confirmed;

            if (available < 1) {
                result.put("success", false);
                result.put("message", "Нет свободных мест в турнире");
                return ResponseEntity.badRequest().body(result);
            }

            // Проверяем, не зарегистрированы ли уже эти игроки
            List<String> alreadyRegistered = new ArrayList<>();
            for (Long playerId : playerIds) {
                String checkSql = "SELECT p.nombre, p.apellido FROM tournament_registrations_db r " +
                        "JOIN player_padel_db p ON p.id = r.player_id " +
                        "WHERE r.tournament_id = ? AND r.player_id = ?";
                try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                    checkPs.setLong(1, tournamentId);
                    checkPs.setLong(2, playerId);
                    ResultSet rs = checkPs.executeQuery();
                    if (rs.next()) {
                        alreadyRegistered.add(rs.getString("nombre") + " " + rs.getString("apellido"));
                    }
                }
            }

            if (!alreadyRegistered.isEmpty()) {
                result.put("success", false);
                result.put("message", "Следующие игроки уже зарегистрированы: " + String.join(", ", alreadyRegistered));
                return ResponseEntity.badRequest().body(result);
            }

            // Получаем следующий position
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

            // Регистрируем обоих игроков как одну команду
            String insertSql = "INSERT INTO tournament_registrations_db " +
                    "(tournament_id, player_id, registration_date, status, position, is_active, is_double_registration, main_player_id) " +
                    "VALUES (?, ?, ?, 'CONFIRMED', ?, true, true, ?)";

            Long mainPlayerId = playerIds.get(0); // первый игрок — главный в паре

            List<Map<String, Object>> registeredPlayers = new ArrayList<>();
            for (Long playerId : playerIds) {
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setLong(1, tournamentId);
                    ps.setLong(2, playerId);
                    ps.setObject(3, LocalDateTime.now());
                    ps.setInt(4, nextPosition);
                    ps.setLong(5, mainPlayerId);

                    int inserted = ps.executeUpdate();
                    if (inserted > 0) {
                        Map<String, Object> playerInfo = new HashMap<>();
                        playerInfo.put("playerId", playerId);
                        playerInfo.put("position", nextPosition);
                        registeredPlayers.add(playerInfo);
                    }
                }
            }

            // Получаем обновленную статистику
            Map<String, Object> stats = getTournamentStats(conn, tournamentId);

            result.put("success", true);
            result.put("message", "Пара успешно зарегистрирована");
            result.put("tournamentId", tournamentId);
            result.put("position", nextPosition);
            result.put("registeredPlayers", registeredPlayers);
            result.put("tournamentStats", stats);

            log.info("Пара успешно зарегистрирована на позицию {}", nextPosition);

        } catch (Exception e) {
            log.error("Критическая ошибка при регистрации пары", e);
            result.put("success", false);
            result.put("message", "Ошибка: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Получение списка доступных игроков для формирования пар
     */
    @GetMapping("/available-players-for-pairs")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAvailablePlayersForPairs(@RequestParam Long tournamentId) {

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> players = new ArrayList<>();

        log.info("Запрос доступных игроков для формирования пар, турнир ID: {}", tournamentId);

        // Показываем всех игроков из БД (без фильтра по activo/email_confirmado — это инструмент администратора)
        String sql = "SELECT p.id, p.nombre, p.apellido, p.email, p.telefono " +
                "FROM player_padel_db p " +
                "WHERE p.id NOT IN ( " +
                "    SELECT player_id FROM tournament_registrations_db WHERE tournament_id = ? " +
                ") " +
                "ORDER BY p.nombre, p.apellido";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, tournamentId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> player = new HashMap<>();
                    player.put("id", rs.getLong("id"));

                    String nombre = rs.getString("nombre");
                    String apellido = rs.getString("apellido");
                    String fullName = nombre + (apellido != null ? " " + apellido : "");

                    player.put("name", fullName);
                    player.put("email", rs.getString("email"));
                    player.put("phone", rs.getString("telefono") != null ? rs.getString("telefono") : "");
                    players.add(player);
                }
            }

            result.put("success", true);
            result.put("players", players);
            result.put("count", players.size());
            result.put("message", "Найдено игроков: " + players.size());

            log.info("Найдено доступных игроков для пар: {}", players.size());

        } catch (Exception e) {
            log.error("Ошибка при получении списка игроков для пар", e);
            result.put("success", false);
            result.put("message", "Ошибка: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Поиск игроков по имени или email
     */
    @GetMapping("/search-players")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> searchPlayers(
            @RequestParam String query,
            @RequestParam(required = false) Long tournamentId) {

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> players = new ArrayList<>();

        String sql = "SELECT id, nombre, apellido, email, telefono " +
                "FROM player_padel_db " +
                "WHERE activo = true AND email_confirmado = true " +
                "AND (LOWER(nombre) LIKE LOWER(?) " +
                "OR LOWER(apellido) LIKE LOWER(?) " +
                "OR LOWER(email) LIKE LOWER(?)) " +
                "ORDER BY nombre, apellido " +
                "LIMIT 20";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            String searchPattern = "%" + query + "%";
            ps.setString(1, searchPattern);
            ps.setString(2, searchPattern);
            ps.setString(3, searchPattern);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> player = new HashMap<>();
                    player.put("id", rs.getLong("id"));

                    String nombre = rs.getString("nombre");
                    String apellido = rs.getString("apellido");
                    String fullName = nombre + (apellido != null ? " " + apellido : "");

                    player.put("name", fullName);
                    player.put("email", rs.getString("email"));
                    player.put("phone", rs.getString("telefono"));
                    players.add(player);
                }
            }

            result.put("success", true);
            result.put("players", players);
            result.put("count", players.size());

        } catch (Exception e) {
            log.error("Ошибка при поиске игроков", e);
            result.put("success", false);
            result.put("message", "Ошибка: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Отладочный метод - просто проверить сколько игроков в БД
     */
    @GetMapping("/debug/player-count")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> debugPlayerCount() {

        Map<String, Object> result = new HashMap<>();

        String sql = "SELECT COUNT(*) as total, " +
                "SUM(CASE WHEN activo THEN 1 ELSE 0 END) as activos, " +
                "SUM(CASE WHEN email_confirmado THEN 1 ELSE 0 END) as confirmados " +
                "FROM player_padel_db";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                result.put("total_players", rs.getInt("total"));
                result.put("active_players", rs.getInt("activos"));
                result.put("confirmed_players", rs.getInt("confirmados"));
                result.put("success", true);
            }

        } catch (Exception e) {
            log.error("Ошибка при подсчете игроков", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Создание нового тестового игрока
     */
    @PostMapping("/create-test-player")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createTestPlayer(@RequestBody Map<String, String> playerData) {

        Map<String, Object> result = new HashMap<>();

        String nombre = playerData.get("nombre");
        String apellido = playerData.get("apellido");
        String email = playerData.get("email");
        String telefono = playerData.get("telefono");

        if (nombre == null || email == null) {
            result.put("success", false);
            result.put("message", "Имя и email обязательны");
            return ResponseEntity.badRequest().body(result);
        }

        // Проверяем, не существует ли уже такой email
        String checkSql = "SELECT COUNT(*) FROM player_padel_db WHERE email = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement checkPs = conn.prepareStatement(checkSql)) {

            checkPs.setString(1, email);
            ResultSet rs = checkPs.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                result.put("success", false);
                result.put("message", "Игрок с таким email уже существует");
                return ResponseEntity.badRequest().body(result);
            }

            // Создаем нового игрока
            String insertSql = "INSERT INTO player_padel_db " +
                    "(nombre, apellido, email, telefono, password_hash, activo, email_confirmado, fecha_registro) " +
                    "VALUES (?, ?, ?, ?, ?, true, true, NOW())";

            try (PreparedStatement insertPs = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                insertPs.setString(1, nombre);
                insertPs.setString(2, apellido);
                insertPs.setString(3, email);
                insertPs.setString(4, telefono);
                insertPs.setString(5, "$2a$10$dummyhash" + System.currentTimeMillis()); // временный хеш

                int inserted = insertPs.executeUpdate();

                if (inserted > 0) {
                    ResultSet generatedKeys = insertPs.getGeneratedKeys();
                    if (generatedKeys.next()) {
                        result.put("id", generatedKeys.getLong(1));
                    }

                    result.put("success", true);
                    result.put("message", "Игрок успешно создан");
                    result.put("player", Map.of(
                            "nombre", nombre,
                            "apellido", apellido,
                            "email", email,
                            "telefono", telefono
                    ));
                } else {
                    result.put("success", false);
                    result.put("message", "Не удалось создать игрока");
                }
            }

        } catch (Exception e) {
            log.error("Ошибка при создании игрока", e);
            result.put("success", false);
            result.put("message", "Ошибка: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Массовое создание тестовых игроков с уникальными email
     */
    @PostMapping("/create-test-players-bulk")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createTestPlayersBulk(@RequestParam(defaultValue = "5") int count) {

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> created = new ArrayList<>();
        int successCount = 0;

        // Получаем текущий максимальный номер тестового игрока
        String getMaxNumberSql = "SELECT COALESCE(MAX(CAST(SUBSTRING(email FROM 'test\\.player(\\d+)@') AS INTEGER)), 0) " +
                "FROM player_padel_db WHERE email LIKE 'test.player%@test.com'";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement maxPs = conn.prepareStatement(getMaxNumberSql);
             ResultSet rs = maxPs.executeQuery()) {

            int startNumber = 1;
            if (rs.next()) {
                startNumber = rs.getInt(1) + 1;
            }

            log.info("Начинаем создание тестовых игроков с номера {}", startNumber);

            String sql = "INSERT INTO player_padel_db " +
                    "(nombre, apellido, email, telefono, password_hash, activo, email_confirmado, fecha_registro) " +
                    "VALUES (?, ?, ?, ?, ?, true, true, NOW())";

            for (int i = 0; i < count; i++) {
                int currentNumber = startNumber + i;
                String email = "test.player" + currentNumber + "@test.com";

                // Проверяем, не существует ли уже такой email
                String checkSql = "SELECT COUNT(*) FROM player_padel_db WHERE email = ?";
                try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                    checkPs.setString(1, email);
                    ResultSet checkRs = checkPs.executeQuery();
                    if (checkRs.next() && checkRs.getInt(1) > 0) {
                        log.warn("Игрок с email {} уже существует, пропускаем", email);
                        continue;
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    String nombre = "Тест" + currentNumber;
                    String apellido = "Игроков";
                    String telefono = "+7 999 123-45-" + String.format("%02d", currentNumber % 100);
                    String passwordHash = "$2a$10$testhash" + System.currentTimeMillis() + currentNumber;

                    ps.setString(1, nombre);
                    ps.setString(2, apellido);
                    ps.setString(3, email);
                    ps.setString(4, telefono);
                    ps.setString(5, passwordHash);

                    int inserted = ps.executeUpdate();

                    if (inserted > 0) {
                        successCount++;
                        Map<String, Object> player = new HashMap<>();
                        player.put("nombre", nombre);
                        player.put("apellido", apellido);
                        player.put("email", email);

                        ResultSet generatedKeys = ps.getGeneratedKeys();
                        if (generatedKeys.next()) {
                            player.put("id", generatedKeys.getLong(1));
                        }

                        created.add(player);
                        log.debug("✅ Создан тестовый игрок: {}", email);
                    }
                } catch (Exception e) {
                    log.warn("Ошибка при создании игрока с номером {}: {}", currentNumber, e.getMessage());
                }
            }

            result.put("success", true);
            result.put("created", successCount);
            result.put("total", count);
            result.put("players", created);
            result.put("message", "Создано игроков: " + successCount + " из " + count);

        } catch (Exception e) {
            log.error("Ошибка при массовом создании игроков", e);
            result.put("success", false);
            result.put("message", "Ошибка: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Удаление тестовых игроков
     */
    @PostMapping("/delete-test-players-bulk")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteTestPlayersBulk(@RequestParam(defaultValue = "5") int count) {

        Map<String, Object> result = new HashMap<>();

        // Шаг 1: находим ID игроков которые будут удалены
        String selectSql = "SELECT id, email FROM player_padel_db " +
                "WHERE email LIKE 'test.player%@test.com' " +
                "ORDER BY id DESC LIMIT ?";

        List<Long> playerIdsToDelete = new ArrayList<>();
        List<Map<String, Object>> deletedInfo = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement selectPs = conn.prepareStatement(selectSql)) {

            selectPs.setInt(1, count);
            try (ResultSet rs = selectPs.executeQuery()) {
                while (rs.next()) {
                    long playerId = rs.getLong("id");
                    playerIdsToDelete.add(playerId);
                    Map<String, Object> info = new HashMap<>();
                    info.put("id", playerId);
                    info.put("email", rs.getString("email"));
                    deletedInfo.add(info);
                }
            }
        } catch (Exception e) {
            log.error("Ошибка при поиске тестовых игроков для удаления", e);
            result.put("success", false);
            result.put("message", "Ошибка поиска: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }

        if (playerIdsToDelete.isEmpty()) {
            result.put("success", true);
            result.put("deleted", 0);
            result.put("message", "Тестовые игроки не найдены");
            return ResponseEntity.ok(result);
        }

        // Шаг 2: отменяем регистрации и обрабатываем листы ожидания ПЕРЕД удалением.
        // Без этого FK ON DELETE CASCADE удалял регистрации напрямую в БД,
        // минуя processWaitlistForTournament — освободившиеся места никому не доставались.
        int cancelledRegistrations = 0;
        for (Long playerId : playerIdsToDelete) {
            try {
                tournamentService.cancelAllRegistrationsForPlayer(playerId);
                cancelledRegistrations++;
            } catch (Exception e) {
                log.warn("Не удалось отменить регистрации для игрока {}: {}", playerId, e.getMessage());
            }
        }
        log.info("Отменены регистрации для {} игроков перед удалением", cancelledRegistrations);

        // Шаг 3: удаляем игроков (CASCADE в FK почистит оставшиеся записи в tournament_registrations_db)
        String deleteSql = "DELETE FROM player_padel_db WHERE id IN (" +
                "SELECT id FROM player_padel_db " +
                "WHERE email LIKE 'test.player%@test.com' " +
                "ORDER BY id DESC LIMIT ?)";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement deletePs = conn.prepareStatement(deleteSql)) {

            deletePs.setInt(1, count);
            int deletedCount = deletePs.executeUpdate();

            result.put("success", true);
            result.put("deleted", deletedCount);
            result.put("requested", count);
            result.put("players", deletedInfo);
            result.put("message", "Удалено игроков: " + deletedCount + " из " + count);

        } catch (Exception e) {
            log.error("Ошибка при удалении тестовых игроков", e);
            result.put("success", false);
            result.put("message", "Ошибка удаления: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Получение количества тестовых игроков
     */
    @GetMapping("/test-players-count")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getTestPlayersCount() {

        Map<String, Object> result = new HashMap<>();

        String sql = "SELECT COUNT(*) as count, MIN(id) as min_id, MAX(id) as max_id " +
                "FROM player_padel_db WHERE email LIKE 'test.player%@test.com'";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                result.put("count", rs.getInt("count"));
                result.put("minId", rs.getInt("min_id"));
                result.put("maxId", rs.getInt("max_id"));
                result.put("success", true);
            }

        } catch (Exception e) {
            log.error("Ошибка при подсчете тестовых игроков", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Простой метод для проверки - вернуть первых 5 игроков
     */
    @GetMapping("/debug/first-players")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getFirstPlayers() {

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> players = new ArrayList<>();

        String sql = "SELECT id, nombre, apellido, email FROM player_padel_db LIMIT 5";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> player = new HashMap<>();
                player.put("id", rs.getLong("id"));
                player.put("nombre", rs.getString("nombre"));
                player.put("apellido", rs.getString("apellido"));
                player.put("email", rs.getString("email"));
                players.add(player);
            }

            result.put("success", true);
            result.put("players", players);
            result.put("count", players.size());

        } catch (Exception e) {
            log.error("Ошибка", e);
            result.put("success", false);
            result.put("message", e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Удаление игрока из турнира
     */
    @PostMapping("/{tournamentId}/remove-player/{playerId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> removePlayerFromTournament(
            @PathVariable Long tournamentId,
            @PathVariable Long playerId) {

        log.info("Удаление игрока {} из турнира {}", playerId, tournamentId);

        Map<String, Object> result = new HashMap<>();

        try {
            tournamentService.adminRemovePlayerRegistration(tournamentId, playerId);
            result.put("success", true);
            result.put("message", "Игрок удален из турнира");
        } catch (Exception e) {
            log.error("Ошибка при удалении игрока из турнира", e);
            result.put("success", false);
            result.put("message", "Ошибка: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Получение списка доступных пар для регистрации на турнир
     */
    @GetMapping("/available-pairs")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAvailablePairs(@RequestParam Long tournamentId) {

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> pairs = new ArrayList<>();

        log.info("Запрос доступных пар для турнира ID: {}", tournamentId);

        // Получаем информацию о турнире
        TournamentDto tournament = tournamentService.getTournamentDtoById(tournamentId).orElse(null);
        if (tournament == null) {
            result.put("success", false);
            result.put("message", "Турнир не найден");
            return ResponseEntity.badRequest().body(result);
        }

        // SQL для получения всех игроков, которые еще не зарегистрированы
        String sql = "SELECT p.id, p.nombre, p.apellido, p.email, p.telefono " +
                "FROM player_padel_db p " +
                "WHERE p.activo = true AND p.email_confirmado = true " +
                "AND p.id NOT IN ( " +
                "    SELECT player_id FROM tournament_registrations_db WHERE tournament_id = ? " +
                ") " +
                "ORDER BY p.nombre, p.apellido";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, tournamentId);
            List<Map<String, Object>> allPlayers = new ArrayList<>();

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> player = new HashMap<>();
                    player.put("id", rs.getLong("id"));

                    String nombre = rs.getString("nombre");
                    String apellido = rs.getString("apellido");
                    String fullName = nombre + (apellido != null ? " " + apellido : "");

                    player.put("name", fullName);
                    player.put("email", rs.getString("email"));
                    player.put("phone", rs.getString("telefono"));
                    allPlayers.add(player);
                }
            }

            // Формируем возможные пары
            for (int i = 0; i < allPlayers.size(); i++) {
                for (int j = i + 1; j < allPlayers.size(); j++) {
                    Map<String, Object> pair = new HashMap<>();
                    Map<String, Object> player1 = allPlayers.get(i);
                    Map<String, Object> player2 = allPlayers.get(j);

                    List<Long> playerIds = new ArrayList<>();
                    playerIds.add((Long) player1.get("id"));
                    playerIds.add((Long) player2.get("id"));

                    pair.put("playerIds", playerIds);
                    pair.put("player1", player1);
                    pair.put("player2", player2);
                    pair.put("displayName", player1.get("name") + " + " + player2.get("name"));
                    pairs.add(pair);
                }
            }

            result.put("success", true);
            result.put("pairs", pairs);
            result.put("count", pairs.size());
            result.put("isDoubles", true);

            log.info("Найдено возможных пар: {}", pairs.size());

        } catch (Exception e) {
            log.error("Ошибка при получении списка пар", e);
            result.put("success", false);
            result.put("message", "Ошибка: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Регистрация гостевой пары (один или оба игрока не в базе данных)
     */
    @PostMapping("/{tournamentId}/register-guest-pair")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> registerGuestPair(
            @PathVariable Long tournamentId,
            @RequestBody Map<String, Object> request) {

        log.info("Регистрация гостевой пары на турнир: {}", tournamentId);

        Map<String, Object> result = new HashMap<>();

        try (Connection conn = dataSource.getConnection()) {
            TournamentDto tournament = tournamentService.getTournamentDtoById(tournamentId).orElse(null);
            if (tournament == null) {
                result.put("success", false);
                result.put("message", "Турнир не найден");
                return ResponseEntity.badRequest().body(result);
            }

            if (!"DOBLES".equals(tournament.getModalidad().name())) {
                result.put("success", false);
                result.put("message", "Гостевая пара только для парных турниров");
                return ResponseEntity.badRequest().body(result);
            }

            Map<String, Object> currentStats = getTournamentStats(conn, tournamentId);
            int confirmed = (int) currentStats.getOrDefault("confirmed", 0);
            if (confirmed >= tournament.getCupoMax()) {
                result.put("success", false);
                result.put("message", "Нет свободных мест в турнире");
                return ResponseEntity.badRequest().body(result);
            }

            Map<String, Object> p1Data = (Map<String, Object>) request.get("player1");
            Map<String, Object> p2Data = (Map<String, Object>) request.get("player2");

            if (p1Data == null || p2Data == null) {
                result.put("success", false);
                result.put("message", "Данные обоих игроков обязательны");
                return ResponseEntity.badRequest().body(result);
            }

            // Сначала проверяем игроков с явным ID — до создания гостевых записей,
            // чтобы не оставлять осиротевших записей при ошибке
            for (Map<String, Object> pData : List.of(p1Data, p2Data)) {
                Object idObj = pData.get("id");
                if (idObj != null) {
                    long existingId = ((Number) idObj).longValue();
                    if (existingId > 0) {
                        String checkSql = "SELECT p.nombre, p.apellido FROM tournament_registrations_db r " +
                                "JOIN player_padel_db p ON p.id = r.player_id " +
                                "WHERE r.tournament_id=? AND r.player_id=?";
                        try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                            ps.setLong(1, tournamentId);
                            ps.setLong(2, existingId);
                            ResultSet rs = ps.executeQuery();
                            if (rs.next()) {
                                String name = rs.getString("nombre") + " " + rs.getString("apellido");
                                result.put("success", false);
                                result.put("message", "Игрок " + name.trim() + " уже зарегистрирован в этом турнире");
                                return ResponseEntity.badRequest().body(result);
                            }
                        }
                    }
                }
            }

            Long player1Id = resolveOrCreateGuestPlayer(conn, p1Data);
            Long player2Id = resolveOrCreateGuestPlayer(conn, p2Data);

            // Финальная проверка — на случай если resolveOrCreate вернул уже зарегистрированного игрока (например по телефону)
            for (Long pid : List.of(player1Id, player2Id)) {
                String checkSql = "SELECT p.nombre, p.apellido FROM tournament_registrations_db r " +
                        "JOIN player_padel_db p ON p.id = r.player_id " +
                        "WHERE r.tournament_id=? AND r.player_id=?";
                try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                    ps.setLong(1, tournamentId);
                    ps.setLong(2, pid);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        String name = rs.getString("nombre") + " " + rs.getString("apellido");
                        result.put("success", false);
                        result.put("message", "Игрок " + name.trim() + " уже зарегистрирован в этом турнире");
                        return ResponseEntity.badRequest().body(result);
                    }
                }
            }

            String maxPosSql = "SELECT COALESCE(MAX(position), 0) FROM tournament_registrations_db WHERE tournament_id=?";
            int nextPosition = 1;
            try (PreparedStatement ps = conn.prepareStatement(maxPosSql)) {
                ps.setLong(1, tournamentId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) nextPosition = rs.getInt(1) + 1;
            }

            String insertSql = "INSERT INTO tournament_registrations_db " +
                    "(tournament_id, player_id, registration_date, status, position, is_active, is_double_registration, main_player_id) " +
                    "VALUES (?, ?, ?, 'CONFIRMED', ?, true, true, ?)";

            for (Long pid : List.of(player1Id, player2Id)) {
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setLong(1, tournamentId);
                    ps.setLong(2, pid);
                    ps.setObject(3, LocalDateTime.now());
                    ps.setInt(4, nextPosition);
                    ps.setLong(5, player1Id);
                    ps.executeUpdate();
                }
            }

            Map<String, Object> stats = getTournamentStats(conn, tournamentId);
            result.put("success", true);
            result.put("message", "Гостевая пара успешно зарегистрирована");
            result.put("position", nextPosition);
            result.put("player1Id", player1Id);
            result.put("player2Id", player2Id);
            result.put("tournamentStats", stats);

            log.info("Гостевая пара зарегистрирована на позицию {}: игроки {} и {}", nextPosition, player1Id, player2Id);

        } catch (Exception e) {
            log.error("Ошибка при регистрации гостевой пары", e);
            result.put("success", false);
            result.put("message", "Ошибка: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Регистрация гостевого игрока на одиночный турнир (игрок не в базе данных)
     */
    @PostMapping("/{tournamentId}/register-guest-player")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> registerGuestPlayer(
            @PathVariable Long tournamentId,
            @RequestBody Map<String, Object> playerData) {

        log.info("Регистрация гостевого игрока на турнир: {}", tournamentId);

        Map<String, Object> result = new HashMap<>();

        try (Connection conn = dataSource.getConnection()) {
            TournamentDto tournament = tournamentService.getTournamentDtoById(tournamentId).orElse(null);
            if (tournament == null) {
                result.put("success", false);
                result.put("message", "Турнир не найден");
                return ResponseEntity.badRequest().body(result);
            }

            if ("DOBLES".equals(tournament.getModalidad().name())) {
                result.put("success", false);
                result.put("message", "Гостевой игрок доступен только для не парных турниров");
                return ResponseEntity.badRequest().body(result);
            }

            Map<String, Object> currentStats = getTournamentStats(conn, tournamentId);
            int confirmed = (int) currentStats.getOrDefault("confirmed", 0);
            if (confirmed >= tournament.getCupoMax()) {
                result.put("success", false);
                result.put("message", "Нет свободных мест в турнире");
                return ResponseEntity.badRequest().body(result);
            }

            Long playerId = resolveOrCreateGuestPlayer(conn, playerData);
            Map<String, Object> regResult = registerPlayer(conn, tournamentId, playerId);

            if (!(boolean) regResult.get("success")) {
                result.put("success", false);
                result.put("message", "already_registered".equals(regResult.get("reason"))
                        ? "Этот игрок уже зарегистрирован в турнире"
                        : "Не удалось зарегистрировать игрока: " + regResult.get("error"));
                return ResponseEntity.badRequest().body(result);
            }

            Map<String, Object> stats = getTournamentStats(conn, tournamentId);
            result.put("success", true);
            result.put("message", "Гостевой игрок успешно зарегистрирован");
            result.put("playerId", playerId);
            result.put("position", regResult.get("position"));
            result.put("tournamentStats", stats);

            log.info("Гостевой игрок {} зарегистрирован на турнир {}", playerId, tournamentId);

        } catch (Exception e) {
            log.error("Ошибка при регистрации гостевого игрока", e);
            result.put("success", false);
            result.put("message", "Ошибка: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Находит игрока по ID или по телефону, либо создаёт нового гостевого игрока
     */
    private Long resolveOrCreateGuestPlayer(Connection conn, Map<String, Object> playerData) throws Exception {
        Object idObj = playerData.get("id");
        if (idObj != null) {
            long id = ((Number) idObj).longValue();
            if (id > 0) {
                String check = "SELECT id FROM player_padel_db WHERE id=?";
                try (PreparedStatement ps = conn.prepareStatement(check)) {
                    ps.setLong(1, id);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) return rs.getLong(1);
                }
            }
        }

        String nombre = (String) playerData.getOrDefault("nombre", "Invitado");
        String apellido = (String) playerData.getOrDefault("apellido", "");
        String telefono = (String) playerData.getOrDefault("telefono", "");

        // Если указан телефон — сначала ищем существующего игрока, чтобы не нарушать уникальность
        if (telefono != null && !telefono.isBlank()) {
            String phoneLookup = "SELECT id FROM player_padel_db WHERE telefono = ?";
            try (PreparedStatement ps = conn.prepareStatement(phoneLookup)) {
                ps.setString(1, telefono);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    long existingId = rs.getLong(1);
                    log.info("Найден существующий игрок по телефону {}: ID={}", telefono, existingId);
                    return existingId;
                }
            }
        }

        String email = "guest." + System.currentTimeMillis() + "@1padel.guest";

        String insertSql = "INSERT INTO player_padel_db " +
                "(nombre, apellido, email, telefono, password_hash, activo, email_confirmado, fecha_registro) " +
                "VALUES (?, ?, ?, NULLIF(?, ''), ?, true, true, NOW())";

        try (PreparedStatement ps = conn.prepareStatement(insertSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.setString(2, apellido);
            ps.setString(3, email);
            ps.setString(4, telefono);
            ps.setString(5, "$2a$10$guest" + System.currentTimeMillis());
            ps.executeUpdate();

            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) {
                long newId = keys.getLong(1);
                log.info("Создан гостевой игрок: {} {} (ID={})", nombre, apellido, newId);
                return newId;
            }
        }
        throw new Exception("Не удалось создать гостевого игрока: " + nombre + " " + apellido);
    }

    /**
     * Регистрация пары на турнир
     */
    @PostMapping("/{tournamentId}/register-pair")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> registerPair(
            @PathVariable Long tournamentId,
            @RequestBody Map<String, Object> request) {

        log.info("Регистрация пары на турнир: {}", tournamentId);

        Map<String, Object> result = new HashMap<>();
        List<Integer> playerIdsInt = (List<Integer>) request.get("playerIds");

        if (playerIdsInt == null || playerIdsInt.size() != 2) {
            result.put("success", false);
            result.put("message", "Для парного турнира нужно выбрать ровно двух игроков");
            return ResponseEntity.badRequest().body(result);
        }

        List<Long> playerIds = new ArrayList<>();
        for (Integer id : playerIdsInt) {
            playerIds.add(id.longValue());
        }

        try (Connection conn = dataSource.getConnection()) {
            // Проверяем существование турнира
            TournamentDto tournament = tournamentService.getTournamentDtoById(tournamentId)
                    .orElse(null);

            if (tournament == null) {
                result.put("success", false);
                result.put("message", "Турнир с ID " + tournamentId + " не найден");
                return ResponseEntity.badRequest().body(result);
            }

            // Проверяем, что турнир парный
            if (!"DOBLES".equals(tournament.getModalidad().name())) {
                result.put("success", false);
                result.put("message", "Этот турнир не является парным");
                return ResponseEntity.badRequest().body(result);
            }

            // Проверяем свободные места
            Map<String, Object> currentStats = getTournamentStats(conn, tournamentId);
            int confirmed = (int) currentStats.getOrDefault("confirmed", 0);
            int available = tournament.getCupoMax() - confirmed;

            if (available < 1) {
                result.put("success", false);
                result.put("message", "Нет свободных мест в турнире");
                return ResponseEntity.badRequest().body(result);
            }

            // Проверяем, не зарегистрированы ли уже эти игроки
            List<String> alreadyRegistered = new ArrayList<>();
            for (Long playerId : playerIds) {
                String checkSql = "SELECT p.nombre, p.apellido FROM tournament_registrations_db r " +
                        "JOIN player_padel_db p ON p.id = r.player_id " +
                        "WHERE r.tournament_id = ? AND r.player_id = ?";
                try (PreparedStatement checkPs = conn.prepareStatement(checkSql)) {
                    checkPs.setLong(1, tournamentId);
                    checkPs.setLong(2, playerId);
                    ResultSet rs = checkPs.executeQuery();
                    if (rs.next()) {
                        alreadyRegistered.add(rs.getString("nombre") + " " + rs.getString("apellido"));
                    }
                }
            }

            if (!alreadyRegistered.isEmpty()) {
                result.put("success", false);
                result.put("message", "Следующие игроки уже зарегистрированы: " + String.join(", ", alreadyRegistered));
                return ResponseEntity.badRequest().body(result);
            }

            // Получаем следующий position
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

            // Регистрируем обоих игроков
            List<Map<String, Object>> registeredPlayers = new ArrayList<>();
            for (Long playerId : playerIds) {
                String insertSql = "INSERT INTO tournament_registrations_db " +
                        "(tournament_id, player_id, registration_date, status, position, is_active, is_double_registration, main_player_id) " +
                        "VALUES (?, ?, ?, 'CONFIRMED', ?, true, true, ?)";

                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setLong(1, tournamentId);
                    ps.setLong(2, playerId);
                    ps.setObject(3, LocalDateTime.now());
                    ps.setInt(4, nextPosition);
                    ps.setLong(5, playerIds.get(0)); // main_player_id - первый игрок в паре

                    int inserted = ps.executeUpdate();
                    if (inserted > 0) {
                        Map<String, Object> playerInfo = new HashMap<>();
                        playerInfo.put("playerId", playerId);
                        playerInfo.put("position", nextPosition);
                        registeredPlayers.add(playerInfo);
                    }
                }
            }

            // Получаем обновленную статистику
            Map<String, Object> stats = getTournamentStats(conn, tournamentId);

            result.put("success", true);
            result.put("message", "Пара успешно зарегистрирована");
            result.put("tournamentId", tournamentId);
            result.put("position", nextPosition);
            result.put("registeredPlayers", registeredPlayers);
            result.put("tournamentStats", stats);

        } catch (Exception e) {
            log.error("Критическая ошибка при регистрации пары", e);
            result.put("success", false);
            result.put("message", "Ошибка: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Получение расширенной статистики турнира с учетом пар
     */
    @GetMapping("/{tournamentId}/stats-enhanced")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getEnhancedStats(@PathVariable Long tournamentId) {

        Map<String, Object> result = new HashMap<>();

        String sql = "SELECT " +
                "COUNT(DISTINCT position) as total_pairs, " +
                "COUNT(*) as total_players, " +
                "SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END) as confirmed_players, " +
                "COUNT(DISTINCT CASE WHEN status = 'CONFIRMED' THEN position END) as confirmed_pairs " +
                "FROM tournament_registrations_db " +
                "WHERE tournament_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, tournamentId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                result.put("totalPairs", rs.getInt("total_pairs"));
                result.put("totalPlayers", rs.getInt("total_players"));
                result.put("confirmedPlayers", rs.getInt("confirmed_players"));
                result.put("confirmedPairs", rs.getInt("confirmed_pairs"));
                result.put("success", true);
            }

        } catch (Exception e) {
            log.error("Ошибка при получении расширенной статистики", e);
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }

        return ResponseEntity.ok(result);
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

        TournamentDto tournament = tournamentService.getTournamentDtoById(tournamentId).orElse(null);
        if (tournament == null) {
            return stats;
        }

        // Считаем только активные регистрации (is_active = true исключает CANCELLED/деактивированных).
        // Ранее COUNT(*) без фильтра давал завышенные числа из-за:
        // 1) orphaned записей при физическом удалении игрока до нашего фикса
        // 2) CANCELLED записей при деактивации игрока
        String totalSql;
        if ("DOBLES".equals(tournament.getModalidad().name())) {
            // Для парных: totalRegistrations = общее число записей,
            // waitlist = уникальные пары в очереди (по mainPlayerId)
            totalSql = "SELECT COUNT(*) as total, " +
                    "(SELECT COUNT(DISTINCT main_player_id) " +
                    " FROM tournament_registrations_db " +
                    " WHERE tournament_id = ? AND status = 'WAITLIST' " +
                    " AND is_double_registration = true AND main_player_id IS NOT NULL" +
                    " AND is_active = true) as waitlist " +
                    "FROM tournament_registrations_db WHERE tournament_id = ? AND is_active = true";
        } else {
            totalSql = "SELECT COUNT(*) as total, " +
                    "SUM(CASE WHEN status = 'WAITLIST' THEN 1 ELSE 0 END) as waitlist " +
                    "FROM tournament_registrations_db WHERE tournament_id = ? AND is_active = true";
        }

        try (PreparedStatement totalPs = conn.prepareStatement(totalSql)) {
            totalPs.setLong(1, tournamentId);
            if ("DOBLES".equals(tournament.getModalidad().name())) {
                totalPs.setLong(2, tournamentId); // второй параметр для подзапроса waitlist
            }
            ResultSet totalRs = totalPs.executeQuery();
            if (totalRs.next()) {
                stats.put("totalRegistrations", totalRs.getInt("total"));
                stats.put("waitlistCount", totalRs.getInt("waitlist"));
            }
        } catch (Exception e) {
            log.error("Ошибка при получении общего количества", e);
        }

        // Получаем подтвержденные регистрации (основной состав)
        String confirmedSql;
        if ("DOBLES".equals(tournament.getModalidad().name())) {
            // Для парных турниров считаем уникальные пары по mainPlayerId.
            // Включаем CONFIRMED + PAIR_REGISTERED + PARTNER_INVITED —
            // во всех трёх случаях место занято.
            // mainPlayerId IS NOT NULL — только запись главного игрока пары.
            confirmedSql = "SELECT COUNT(DISTINCT main_player_id) as confirmed " +
                    "FROM tournament_registrations_db " +
                    "WHERE tournament_id = ? " +
                    "AND status IN ('CONFIRMED', 'PAIR_REGISTERED', 'PARTNER_INVITED') " +
                    "AND is_double_registration = true " +
                    "AND is_active = true " +
                    "AND main_player_id IS NOT NULL";
        } else {
            confirmedSql = "SELECT COUNT(*) as confirmed " +
                    "FROM tournament_registrations_db " +
                    "WHERE tournament_id = ? " +
                    "AND status NOT IN ('WAITLIST', 'CANCELLED') " +
                    "AND is_active = true";
        }

        if ("DOBLES".equals(tournament.getModalidad().name())) {
            // Считаем реальное кол-во уникальных игроков (записей с is_active=true)
            String totalPlayersSql = "SELECT COUNT(*) FROM tournament_registrations_db " +
                    "WHERE tournament_id = ? AND is_active = true " +
                    "AND status IN ('CONFIRMED', 'PAIR_REGISTERED', 'PARTNER_INVITED')";
            try (PreparedStatement ps = conn.prepareStatement(totalPlayersSql)) {
                ps.setLong(1, tournamentId);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    stats.put("totalPlayers", rs.getInt(1));
                }
            } catch (Exception e) {
                log.error("Ошибка при подсчёте total players для DOBLES", e);
            }
        }

        try (PreparedStatement confPs = conn.prepareStatement(confirmedSql)) {
            confPs.setLong(1, tournamentId);
            ResultSet confRs = confPs.executeQuery();
            if (confRs.next()) {
                stats.put("confirmed", confRs.getInt("confirmed"));
                stats.put("available", tournament.getCupoMax() - confRs.getInt("confirmed"));
            }
        } catch (Exception e) {
            log.error("Ошибка при получении подтвержденных", e);
        }

        stats.put("maxAllowed", tournament.getCupoMax());

        return stats;
    }

    /**
     * Добавление игрока в лист ожидания
     */
    private Map<String, Object> addToWaitlist(Connection conn, Long tournamentId, Long playerId, int waitlistPosition) {
        Map<String, Object> result = new HashMap<>();
        result.put("playerId", playerId);

        try {
            String insertSql = "INSERT INTO tournament_registrations_db " +
                    "(tournament_id, player_id, registration_date, status, waitlist_position, is_active) " +
                    "VALUES (?, ?, ?, 'WAITLIST', ?, true)";

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setLong(1, tournamentId);
                ps.setLong(2, playerId);
                ps.setObject(3, LocalDateTime.now());
                ps.setInt(4, waitlistPosition);

                int inserted = ps.executeUpdate();

                result.put("success", inserted > 0);
                result.put("status", "WAITLIST");
                result.put("waitlistPosition", waitlistPosition);
                result.put("timestamp", LocalDateTime.now().toString());
            }

        } catch (Exception e) {
            log.error("Ошибка добавления в резерв игрока {}", playerId, e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }
}