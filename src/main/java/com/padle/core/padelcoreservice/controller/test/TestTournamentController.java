package com.padle.core.padelcoreservice.controller.test;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
import com.padle.core.padelcoreservice.security.RequestIdFilter;
import org.slf4j.MDC;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.context.i18n.LocaleContextHolder;
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
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/test/tournaments")
@RequiredArgsConstructor
public class TestTournamentController {

    private final TournamentService tournamentService;
    private final DataSource dataSource;
    private final MessageSource messages;
    private final PasswordEncoder passwordEncoder;

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
            if (e instanceof SQLException sqlFailure && "23503".equals(sqlFailure.getSQLState())) {
                return registrationResponse(409, "linked_history");
            }
            return registrationFailed(tournamentId, e);
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
                            "WHERE tournament_id = ? AND player_id = ? AND is_active";
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
                            confirmed++;
                        } else if ("already_registered".equals(regResult.get("reason"))) {
                            skipCount++;
                        } else {
                            errors.add(regResult);
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
                    errors.add(registrationErrorDetails(tournamentId, playerId, e));
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
     * Получение списка доступных игроков для формирования пар
     */
    @GetMapping("/available-players-for-pairs")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getAvailablePlayersForPairs(@RequestParam Long tournamentId) {

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> players = new ArrayList<>();

        log.info("Запрос доступных игроков для формирования пар, турнир ID: {}", tournamentId);

        // Отменённая заявка не исключает активный профиль из выбора.
        String sql = "SELECT p.id, p.nombre, p.apellido, p.email, p.telefono " +
                "FROM player_padel_db p " +
                "WHERE p.activo AND p.id NOT IN ( " +
                "    SELECT player_id FROM tournament_registrations_db WHERE tournament_id = ? AND is_active " +
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
                "    SELECT player_id FROM tournament_registrations_db WHERE tournament_id = ? AND is_active " +
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

    public record ManualPlayer(Long id, String nombre, String apellido, String telefono) {}
    public record ManualPair(ManualPlayer player1, ManualPlayer player2) {}
    public record SelectedPair(List<Long> playerIds) {}

    @PostMapping("/{tournamentId}/register-guest-pair")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> registerGuestPair(
            @PathVariable Long tournamentId, @RequestBody ManualPair request,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID requestKey) {
        if (request.player1() == null || request.player2() == null) {
            return registrationResponse(400, "invalid_pair");
        }
        return registerManual(tournamentId, List.of(request.player1(), request.player2()), requestKey);
    }

    @PostMapping({"/{tournamentId}/register-pair", "/{tournamentId}/register-pair-with-players"})
    @ResponseBody
    public ResponseEntity<Map<String, Object>> registerPair(
            @PathVariable Long tournamentId, @RequestBody SelectedPair request) {
        if (request.playerIds() == null || request.playerIds().size() != 2
                || request.playerIds().stream().anyMatch(id -> id == null || id <= 0)) {
            return registrationResponse(400, "invalid_pair");
        }
        return registerManual(tournamentId, request.playerIds().stream()
                .map(id -> new ManualPlayer(id, null, null, null)).toList(), null);
    }

    @PostMapping("/{tournamentId}/register-guest-player")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> registerGuestPlayer(
            @PathVariable Long tournamentId, @RequestBody ManualPlayer player,
            @RequestHeader(value = "Idempotency-Key", required = false) UUID requestKey) {
        return registerManual(tournamentId, List.of(player), requestKey);
    }

    private ResponseEntity<Map<String, Object>> registerManual(
            Long tournamentId, List<ManualPlayer> players, UUID requestKey) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                boolean doubles = players.size() == 2;
                int capacity = lockRegistrationTournament(conn, tournamentId, doubles);
                String fingerprint = requestKey == null ? null : registrationFingerprint(players);
                var previous = requestKey == null ? null : previousManualRegistration(conn, tournamentId, requestKey, fingerprint);
                if (previous != null) {
                    conn.commit();
                    return ResponseEntity.ok(previous);
                }
                lockManualPhones(conn, players);
                List<Long> ids = new ArrayList<>(players.size());
                for (ManualPlayer player : players) {
                    ids.add(resolveOrCreateGuestPlayer(conn, player));
                }
                if (doubles && ids.get(0).equals(ids.get(1))) {
                    throw new ManualRegistrationRejected("same_player");
                }
                var result = saveManualRegistration(conn, tournamentId, ids, capacity);
                if (requestKey != null) {
                    saveManualRequest(conn, tournamentId, requestKey, fingerprint, ids);
                }
                conn.commit();
                return ResponseEntity.ok(result);
            } catch (SQLException | RuntimeException failure) {
                conn.rollback();
                throw failure;
            }
        } catch (ManualRegistrationRejected rejected) {
            return registrationResponse(400, rejected.code);
        } catch (SQLException | RuntimeException failure) {
            return registrationFailed(tournamentId, failure);
        }
    }

    private int lockRegistrationTournament(Connection conn, Long tournamentId, boolean doubles) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(
                "SELECT modalidad, cupo_max FROM tournaments_db WHERE id = ? FOR UPDATE")) {
            statement.setLong(1, tournamentId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) throw new ManualRegistrationRejected("tournament_missing");
                if (doubles != "DOBLES".equals(row.getString("modalidad"))) {
                    throw new ManualRegistrationRejected(doubles ? "requires_doubles" : "requires_singles");
                }
                return row.getInt("cupo_max");
            }
        }
    }

    private Map<String, Object> saveManualRegistration(Connection conn, Long tournamentId,
                                                        List<Long> ids, int capacity) throws SQLException {
        boolean doubles = ids.size() == 2;
        Map<String, Object> stats = getTournamentStats(conn, tournamentId);
        Integer position = activeRegistrationPosition(conn, tournamentId, ids);
        if (position == null) {
            if ((int) stats.get("confirmed") >= capacity) throw new ManualRegistrationRejected("full");
            if (doubles) requirePreviousPairInactive(conn, tournamentId, ids);
            position = nextRegistrationPosition(conn, tournamentId);
            LocalDateTime bookedAt = LocalDateTime.now();
            UUID bookingId = doubles ? UUID.randomUUID() : null;
            for (int i = 0; i < ids.size(); i++) {
                saveRegistration(conn, tournamentId, ids.get(i), doubles ? ids.get(1 - i) : null,
                        doubles ? ids.get(0) : null, bookingId, bookedAt, position, "CONFIRMED", null);
            }
            stats = getTournamentStats(conn, tournamentId);
        }
        return manualSuccess(tournamentId, ids, position, stats);
    }

    private void requirePreviousPairInactive(Connection conn, long tournamentId, List<Long> ids) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                SELECT 1 FROM tournament_registrations_db prior JOIN tournament_registrations_db remaining
                    ON remaining.tournament_id = prior.tournament_id AND remaining.player_id <> prior.player_id
                   AND remaining.is_active AND remaining.is_double_registration
                   AND (remaining.pair_booking_id = prior.pair_booking_id
                     OR (remaining.pair_booking_id IS NULL AND prior.pair_booking_id IS NULL
                       AND remaining.main_player_id = prior.main_player_id))
                WHERE prior.tournament_id = ? AND prior.player_id IN (?, ?) AND NOT prior.is_active
                LIMIT 1
                """)) {
            statement.setLong(1, tournamentId);
            statement.setLong(2, ids.get(0));
            statement.setLong(3, ids.get(1));
            try (ResultSet row = statement.executeQuery()) {
                if (row.next()) throw new ManualRegistrationRejected("previous_pair_active");
            }
        }
    }

    private Map<String, Object> manualSuccess(Long tournamentId, List<Long> ids, int position,
                                               Map<String, Object> stats) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", registrationMessage("success"));
        result.put("position", position);
        result.put("tournamentId", tournamentId);
        result.put("tournamentStats", stats);
        if (ids.size() == 2) {
            result.put("player1Id", ids.get(0));
            result.put("player2Id", ids.get(1));
            result.put("registeredPlayers", ids.stream()
                    .map(id -> Map.of("playerId", id, "position", position)).toList());
        } else {
            result.put("playerId", ids.get(0));
        }
        return result;
    }

    private String registrationFingerprint(List<ManualPlayer> players) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (ManualPlayer player : players) {
                for (Object field : new Object[]{player.id(), player.nombre(), player.apellido(), player.telefono()}) {
                    byte[] bytes = java.util.Objects.toString(field, "").getBytes(StandardCharsets.UTF_8);
                    digest.update(java.nio.ByteBuffer.allocate(Integer.BYTES).putInt(field == null ? -1 : bytes.length).array());
                    digest.update(bytes);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private Map<String, Object> previousManualRegistration(Connection conn, long tournamentId, UUID requestKey,
                                                            String fingerprint) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                SELECT req.fingerprint, req.first_player_id, req.second_player_id,
                       first.is_active AND first.cancellation_version = req.first_version
                         AND (req.second_player_id IS NULL OR
                              second.is_active AND second.cancellation_version = req.second_version) AS current
                FROM admin_registration_requests_db req
                LEFT JOIN tournament_registrations_db first
                  ON first.tournament_id = req.tournament_id AND first.player_id = req.first_player_id
                LEFT JOIN tournament_registrations_db second
                  ON second.tournament_id = req.tournament_id AND second.player_id = req.second_player_id
                WHERE req.tournament_id = ? AND req.request_key = ?
                """)) {
            statement.setLong(1, tournamentId);
            statement.setObject(2, requestKey);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return null;
                if (!row.getBoolean("current") || !fingerprint.equals(row.getString("fingerprint"))) {
                    throw new ManualRegistrationRejected("request_conflict");
                }
                long first = row.getLong("first_player_id");
                Long second = row.getObject("second_player_id", Long.class);
                var ids = second == null ? List.of(first) : List.of(first, second);
                Integer position = activeRegistrationPosition(conn, tournamentId, ids);
                if (position == null) throw new ManualRegistrationRejected("request_conflict");
                return manualSuccess(tournamentId, ids, position, getTournamentStats(conn, tournamentId));
            }
        }
    }

    private void saveManualRequest(Connection conn, long tournamentId, UUID requestKey,
                                   String fingerprint, List<Long> ids) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                INSERT INTO admin_registration_requests_db
                    (tournament_id, request_key, fingerprint, first_player_id, second_player_id, first_version, second_version)
                SELECT first.tournament_id, ?, ?, first.player_id, second.player_id,
                       first.cancellation_version, second.cancellation_version
                FROM tournament_registrations_db first LEFT JOIN tournament_registrations_db second
                  ON second.tournament_id = first.tournament_id AND second.player_id = ?
                WHERE first.tournament_id = ? AND first.player_id = ?
                """)) {
            statement.setObject(1, requestKey);
            statement.setString(2, fingerprint);
            statement.setObject(3, ids.size() == 2 ? ids.get(1) : null);
            statement.setLong(4, tournamentId);
            statement.setLong(5, ids.get(0));
            statement.executeUpdate();
        }
    }

    private Integer activeRegistrationPosition(Connection conn, Long tournamentId, List<Long> ids) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                SELECT position, partner_id, is_double_registration, pair_booking_id, player_id, status
                FROM tournament_registrations_db
                WHERE tournament_id = ? AND is_active AND player_id IN (?, ?)
                """)) {
            statement.setLong(1, tournamentId);
            statement.setLong(2, ids.get(0));
            statement.setLong(3, ids.get(ids.size() - 1));
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                int position = rows.getInt("position");
                if (ids.size() == 1 && !rows.getBoolean("is_double_registration")
                        && "CONFIRMED".equals(rows.getString("status"))) return position;
                if (ids.size() == 2 && matchesActivePair(rows, ids)) return position;
                throw new ManualRegistrationRejected("already_registered");
            }
        }
    }

    private boolean matchesActivePair(ResultSet rows, List<Long> ids) throws SQLException {
        UUID booking = rows.getObject("pair_booking_id", UUID.class);
        long player = rows.getLong("player_id");
        long partner = rows.getLong("partner_id");
        return booking != null && player != partner && ids.contains(player) && ids.contains(partner)
                && rows.getBoolean("is_double_registration") && "CONFIRMED".equals(rows.getString("status"))
                && rows.next() && booking.equals(rows.getObject("pair_booking_id", UUID.class))
                && rows.getLong("player_id") == partner && rows.getLong("partner_id") == player
                && rows.getBoolean("is_double_registration") && "CONFIRMED".equals(rows.getString("status"));
    }

    private Long resolveOrCreateGuestPlayer(Connection conn, ManualPlayer player) throws SQLException {
        if (player.id() != null) {
            try (PreparedStatement statement = conn.prepareStatement("SELECT id FROM player_padel_db WHERE id = ? AND activo")) {
                statement.setLong(1, player.id());
                try (ResultSet row = statement.executeQuery()) {
                    if (row.next()) return row.getLong(1);
                }
            }
            throw new ManualRegistrationRejected("player_missing");
        }
        if (player.nombre() == null || player.nombre().isBlank()) {
            throw new ManualRegistrationRejected("name_required");
        }
        String phone = java.util.Objects.requireNonNullElse(player.telefono(), "").strip();
        Long byPhone = findGuestByPhone(conn, phone);
        if (byPhone != null) return byPhone;
        String email = "guest." + UUID.randomUUID() + "@1padel.guest";
        try (PreparedStatement statement = conn.prepareStatement("""
                INSERT INTO player_padel_db (nombre, apellido, email, telefono, password_hash,
                    activo, email_confirmado, fecha_registro)
                VALUES (?, ?, ?, NULLIF(?, ''), ?, true, true, NOW())
                RETURNING id
                """)) {
            statement.setString(1, player.nombre().strip());
            statement.setString(2, java.util.Objects.requireNonNullElse(player.apellido(), "").strip());
            statement.setString(3, email);
            statement.setString(4, phone);
            statement.setString(5, passwordEncoder.encode(UUID.randomUUID().toString()));
            try (ResultSet row = statement.executeQuery()) {
                if (row.next()) return row.getLong(1);
            }
        }
        throw new SQLException("Вставка гостевого профиля не вернула ID");
    }

    private String phoneKey(String phone) {
        String digits = java.util.Objects.requireNonNullElse(phone, "").replaceAll("[^0-9]", "");
        return digits.substring(Math.max(0, digits.length() - 10));
    }

    private void lockManualPhones(Connection conn, List<ManualPlayer> players) throws SQLException {
        var keys = players.stream().filter(player -> player.id() == null)
                .map(player -> phoneKey(player.telefono())).filter(key -> !key.isEmpty()).distinct().sorted().toList();
        try (PreparedStatement lock = conn.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
            for (String key : keys) {
                lock.setString(1, "partner-phone:" + key);
                lock.execute();
            }
        }
    }

    private Long findGuestByPhone(Connection conn, String phone) throws SQLException {
        String phoneKey = phoneKey(phone);
        if (phoneKey.isEmpty()) return null;
        try (PreparedStatement statement = conn.prepareStatement("""
                SELECT id, activo FROM player_padel_db WHERE telefono IS NOT NULL
                AND RIGHT(regexp_replace(telefono, '[^0-9]', '', 'g'), 10) = ?
                """)) {
            statement.setString(1, phoneKey);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return null;
                long id = row.getLong("id");
                if (!row.getBoolean("activo") || row.next()) throw new ManualRegistrationRejected("ambiguous_phone");
                return id;
            }
        }
    }

    private String registrationMessage(String code) {
        return messages.getMessage("admin.manual_registration." + code, null, LocaleContextHolder.getLocale());
    }

    private ResponseEntity<Map<String, Object>> registrationResponse(int status, String code) {
        return ResponseEntity.status(status).body(Map.of("success", false, "code", code, "message", registrationMessage(code)));
    }

    private ResponseEntity<Map<String, Object>> registrationFailed(Long tournamentId, Exception failure) {
        String requestId = java.util.Objects.requireNonNullElseGet(MDC.get(RequestIdFilter.MDC_KEY),
                () -> UUID.randomUUID().toString().substring(0, 8));
        log.error("Ручная регистрация не сохранена: tournamentId={}, requestId={}, type={}, sqlState={}",
                tournamentId, requestId, failure.getClass().getSimpleName(),
                failure instanceof SQLException sql ? sql.getSQLState() : null);
        return ResponseEntity.internalServerError().body(Map.of("success", false, "code", "technical",
                "message", registrationMessage("technical"), "requestId", requestId));
    }

    private static class ManualRegistrationRejected extends RuntimeException {
        private final String code;

        ManualRegistrationRejected(String code) {
            super(code);
            this.code = code;
        }
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



    private int nextRegistrationPosition(Connection conn, Long tournamentId) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(
                "SELECT COALESCE(MAX(position), 0) + 1 FROM tournament_registrations_db WHERE tournament_id = ?")) {
            statement.setLong(1, tournamentId);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getInt(1);
            }
        }
    }

    /**
     * На ID регистрации ссылаются оплаты, атрибуция и история отмен. Поля новой заявки
     * заменяются, а старый идентификатор пары и её одноразовые ссылки не переносятся.
     */
    private void saveRegistration(Connection conn, Long tournamentId, Long playerId, Long partnerId,
                                  Long mainPlayerId, UUID bookingId, LocalDateTime bookedAt, Integer position,
                                  String status, Integer waitlistPosition) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement("""
                WITH saved AS (
                INSERT INTO tournament_registrations_db AS previous
                    (tournament_id, player_id, registration_date, queued_at, status, position, waitlist_position,
                     is_active, is_double_registration, main_player_id, partner_id, pair_booking_id, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, true, ?, ?, ?, ?, NOW())
                ON CONFLICT (tournament_id, player_id) DO UPDATE SET
                    registration_date = EXCLUDED.registration_date, queued_at = EXCLUDED.queued_at,
                    status = EXCLUDED.status, position = EXCLUDED.position, waitlist_position = EXCLUDED.waitlist_position,
                    is_active = true, updated_at = NOW(),
                    is_double_registration = EXCLUDED.is_double_registration, main_player_id = EXCLUDED.main_player_id,
                    partner_id = EXCLUDED.partner_id, pair_booking_id = EXCLUDED.pair_booking_id,
                    partner_first_name = NULL, partner_last_name = NULL, partner_phone = NULL,
                    partner_email = NULL, partner_telegram = NULL, pair_booked_by = NULL,
                    pair_terms_version = NULL, pair_terms_accepted_at = NULL, pair_terms_accepted_by = NULL,
                    pair_terms_price_per_player = NULL, pair_terms_currency = NULL, pair_terms_free_until = NULL,
                    pair_terms_late_fraction = NULL, partner_registration_token = NULL, partner_token_expiry = NULL,
                    waitlist_confirmation_token = NULL, invitation_expires_at = NULL, invitation_attempts = 0,
                    notified_about_vacancy = false, partner_action_token = NULL, pair_proposer_reg_id = NULL,
                    share_contacts = false, solo_reminder_sent = false, final_reminder_sent = false,
                    attended = false, participation_confirmed = false, late_arrival = false, replacement_search = false
                WHERE NOT previous.is_active
                RETURNING id
                ), expired_requests AS (
                    UPDATE participation_requests_db SET superseded_at = NOW()
                    WHERE registration_id IN (SELECT id FROM saved) AND superseded_at IS NULL
                )
                SELECT id FROM saved
                """)) {
            statement.setLong(1, tournamentId);
            statement.setLong(2, playerId);
            statement.setObject(3, bookedAt);
            statement.setObject(4, bookingId == null ? null : bookedAt);
            statement.setString(5, status);
            statement.setObject(6, position);
            statement.setObject(7, waitlistPosition);
            statement.setBoolean(8, bookingId != null);
            statement.setObject(9, mainPlayerId);
            statement.setObject(10, partnerId);
            statement.setObject(11, bookingId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) throw new ManualRegistrationRejected("already_registered");

            }
        }
    }

    private Map<String, Object> registerPlayer(Connection conn, Long tournamentId, Long playerId) {
        try {
            int position = nextRegistrationPosition(conn, tournamentId);
            LocalDateTime now = LocalDateTime.now();
            saveRegistration(conn, tournamentId, playerId, null, null, null, now, position, "CONFIRMED", null);
            return Map.of("success", true, "playerId", playerId, "status", "REGISTERED",
                    "position", position, "timestamp", now.toString());
        } catch (ManualRegistrationRejected rejected) {
            return Map.of("success", false, "playerId", playerId, "reason", rejected.code);
        } catch (SQLException failure) {
            return registrationErrorDetails(tournamentId, playerId, failure);
        }
    }

    private Map<String, Object> registrationErrorDetails(Long tournamentId, Long playerId, Exception failure) {
        var response = registrationFailed(tournamentId, failure).getBody();
        var details = new HashMap<>(response);
        details.put("playerId", playerId);
        details.put("reason", "exception");
        details.put("error", response.get("message"));
        return details;
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
            confirmedSql = "SELECT COUNT(DISTINCT " + TournamentRegistrationRepository.OCCUPIED_PAIR_KEY
                    + ") as confirmed FROM tournament_registrations_db WHERE tournament_id = ?"
                    + TournamentRegistrationRepository.OCCUPIED_PAIR_FILTER;
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
        try {
            LocalDateTime now = LocalDateTime.now();
            saveRegistration(conn, tournamentId, playerId, null, null, null, now, null, "WAITLIST", waitlistPosition);
            return Map.of("success", true, "playerId", playerId, "status", "WAITLIST",
                    "waitlistPosition", waitlistPosition, "timestamp", now.toString());
        } catch (ManualRegistrationRejected rejected) {
            return Map.of("success", false, "playerId", playerId, "reason", rejected.code);
        } catch (SQLException failure) {
            return registrationErrorDetails(tournamentId, playerId, failure);
        }
    }
}