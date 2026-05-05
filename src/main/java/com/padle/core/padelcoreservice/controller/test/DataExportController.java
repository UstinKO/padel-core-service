package com.padle.core.padelcoreservice.controller.test;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Controller
@RequestMapping("/admin/export")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('OWNER')")
public class DataExportController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping
    @Transactional(readOnly = true)
    public void exportAllData(HttpServletResponse response) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
        String filename = "padel_db_export_" + timestamp + ".xlsx";

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            CellStyle dateStyle = createDateStyle(workbook);

            // ─── 1. Клубы ───────────────────────────────────────────
            addSheetFromQuery(workbook, "Clubes", headerStyle, dataStyle, dateStyle,
                    "SELECT id, nombre, direccion, zona_ciudad, telefono_contacto, email_contacto, " +
                            "mapa_url, website_url, descripcion, logo_url, is_active, created_at, updated_at " +
                            "FROM club_db ORDER BY id");

            // ─── 2. Владельцы/админы ────────────────────────────────
            addSheetFromSQL(workbook, "Administradores", headerStyle, dataStyle, dateStyle,
                    "owners_db",
                    "SELECT id, email, first_name, last_name, phone, role, is_active, " +
                            "last_login_at, created_at, updated_at " +
                            "FROM owners_db ORDER BY id",
                    Arrays.asList("ID", "Email", "Nombre", "Apellido", "Teléfono", "Rol",
                            "Activo", "Último Login", "Creado", "Actualizado"));

            // ─── 3. Игроки ──────────────────────────────────────────
            addSheetFromQuery(workbook, "Jugadores", headerStyle, dataStyle, dateStyle,
                    "SELECT id, nombre, apellido, email, telefono, telegram_username, " +
                            "email_confirmado, fecha_confirmacion_email, activo, oauth2_user, provider, " +
                            "fecha_registro, fecha_actualizacion " +
                            "FROM player_padel_db ORDER BY id");

            // ─── 4. Турниры + название клуба ────────────────────────
            addSheetFromSpan(workbook, "Torneos", headerStyle, dataStyle, dateStyle,
                    "SELECT t.id, c.nombre AS club_nombre, t.nombre, t.fecha_inicio, t.hora_inicio, " +
                            "t.duracion, t.genero_formato, t.categoria_nivel, t.tipo, t.modalidad, " +
                            "t.cupo_max, t.precio, t.moneda, t.estado, t.deadline_cancelacion, " +
                            "t.contacto_organizador, t.faq_url, t.is_active, " +
                            "t.created_at, t.updated_at, t.owner_id " +
                            "FROM tournaments_db t " +
                            "LEFT JOIN club_db c ON t.club_id = c.id " +
                            "ORDER BY t.id");

            // ─── 5. Регистрации на турниры (с именами) ─────────────
            addSheetFromSpan(workbook, "Registros", headerStyle, dataStyle, dateStyle,
                    "SELECT r.id, t.nombre AS torneo, " +
                            "CONCAT(p.nombre, ' ', p.apellido) AS jugador, p.email AS email_jugador, " +
                            "r.status, r.position, r.waitlist_position, " +
                            "CONCAT(COALESCE(par.nombre,''), ' ', COALESCE(par.apellido,'')) AS pareja, " +
                            "r.is_double_registration, r.registration_date, r.cancellation_date, " +
                            "r.cancellation_reason, r.attended, r.is_active " +
                            "FROM tournament_registrations_db r " +
                            "JOIN tournaments_db t ON r.tournament_id = t.id " +
                            "JOIN player_padel_db p ON r.player_id = p.id " +
                            "LEFT JOIN player_padel_db par ON r.partner_id = par.id " +
                            "ORDER BY r.id");

            // ─── 6. Платежи ─────────────────────────────────────────
            addSheetFromSpan(workbook, "Pagos", headerStyle, dataStyle, dateStyle,
                    "SELECT pay.id, t.nombre AS torneo, " +
                            "CONCAT(p.nombre, ' ', p.apellido) AS jugador, " +
                            "pay.amount, pay.currency, pay.status, pay.payment_method, " +
                            "pay.payment_date, pay.transaction_id, pay.payment_provider, pay.notes, " +
                            "pay.is_active, pay.created_at " +
                            "FROM payments_db pay " +
                            "JOIN tournament_registrations_db r ON pay.registration_id = r.id " +
                            "JOIN tournaments_db t ON r.tournament_id = t.id " +
                            "JOIN player_padel_db p ON r.player_id = p.id " +
                            "ORDER BY pay.id");

            // ─── 7. Рейтинг ─────────────────────────────────────────
            addSheetFromSpan(workbook, "Ranking", headerStyle, dataStyle, dateStyle,
                    "SELECT r.id, CONCAT(p.nombre, ' ', p.apellido) AS jugador, p.email, " +
                            "r.puntos, r.torneos_jugados, r.torneos_ganados, " +
                            "r.partidos_ganados, r.partidos_perdidos, r.sets_ganados, r.sets_perdidos, " +
                            "r.nivel_actual, r.posicion_actual, r.posicion_anterior, r.mejor_posicion, " +
                            "r.rachas_actual, r.rachas_maxima, r.last_calculated_at " +
                            "FROM ranking_db r " +
                            "JOIN player_padel_db p ON r.player_id = p.id " +
                            "ORDER BY r.posicion_actual");

            // ─── 8. Токены сброса пароля ───────────────────────────
            addSheetFromSpan(workbook, "Tokens_Reset", headerStyle, dataStyle, dateStyle,
                    "SELECT t.id, CONCAT(p.nombre, ' ', p.apellido) AS jugador, p.email, " +
                            "t.token, t.expiry_date, t.used, t.created_at " +
                            "FROM password_reset_tokens_db t " +
                            "JOIN player_padel_db p ON t.player_id = p.id " +
                            "ORDER BY t.id");

            // ─── 9. King of Court турниры ──────────────────────────
            addSheetFromSpan(workbook, "KingOfCourt_Torneos", headerStyle, dataStyle, dateStyle,
                    "SELECT k.id, t.nombre AS torneo, c.nombre AS club, " +
                            "k.max_courts, k.calibration_rounds, k.current_round, " +
                            "k.is_active, k.is_finished, k.started_at, k.finished_at, " +
                            "k.youtube_link, k.created_at " +
                            "FROM tournament_king_of_court_db k " +
                            "JOIN tournaments_db t ON k.tournament_id = t.id " +
                            "LEFT JOIN club_db c ON t.club_id = c.id " +
                            "ORDER BY k.id");

            // ─── 10. King of Court раунды ──────────────────────────
            addSheetFromSpan(workbook, "KingOfCourt_Rondas", headerStyle, dataStyle, dateStyle,
                    "SELECT r.id, t.nombre AS torneo, r.round_number, r.is_completed, " +
                            "r.created_at, r.completed_at " +
                            "FROM king_of_court_round_db r " +
                            "JOIN tournament_king_of_court_db k ON r.tournament_king_id = k.id " +
                            "JOIN tournaments_db t ON k.tournament_id = t.id " +
                            "ORDER BY r.id");

            // ─── 11. King of Court корты ───────────────────────────
            addSheetFromSpan(workbook, "KingOfCourt_Cortes", headerStyle, dataStyle, dateStyle,
                    "SELECT c.id, t.nombre AS torneo, r.round_number, c.court_number " +
                            "FROM king_of_court_court_db c " +
                            "JOIN king_of_court_round_db r ON c.round_id = r.id " +
                            "JOIN tournament_king_of_court_db k ON r.tournament_king_id = k.id " +
                            "JOIN tournaments_db t ON k.tournament_id = t.id " +
                            "ORDER BY c.id");

            // ─── 12. King of Court игроки на кортах ────────────────
            addSheetFromSpan(workbook, "KingOfCourt_CourtPlayers", headerStyle, dataStyle, dateStyle,
                    "SELECT cp.court_id, t.nombre AS torneo, r.round_number, c.court_number, " +
                            "CONCAT(p.nombre, ' ', p.apellido) AS jugador " +
                            "FROM court_players_db cp " +
                            "JOIN king_of_court_court_db c ON cp.court_id = c.id " +
                            "JOIN king_of_court_round_db r ON c.round_id = r.id " +
                            "JOIN tournament_king_of_court_db k ON r.tournament_king_id = k.id " +
                            "JOIN tournaments_db t ON k.tournament_id = t.id " +
                            "JOIN player_padel_db p ON cp.player_id = p.id " +
                            "ORDER BY cp.court_id, cp.player_id");

            // ─── 13. King of Court команды на кортах ───────────────
            addSheetFromSpan(workbook, "KingOfCourt_CourtTeams", headerStyle, dataStyle, dateStyle,
                    "SELECT ct.id, ct.court_id, ct.team_number, " +
                            "CONCAT(p1.nombre, ' ', p1.apellido) AS jugador1, " +
                            "CONCAT(COALESCE(p2.nombre,''), ' ', COALESCE(p2.apellido,'')) AS jugador2 " +
                            "FROM court_teams_db ct " +
                            "LEFT JOIN player_padel_db p1 ON ct.player1_id = p1.id " +
                            "LEFT JOIN player_padel_db p2 ON ct.player2_id = p2.id " +
                            "ORDER BY ct.id");

            // ─── 14. King of Court результаты матчей ───────────────
            addSheetFromSpan(workbook, "KingOfCourt_Resultados", headerStyle, dataStyle, dateStyle,
                    "SELECT mr.id, mr.court_id, " +
                            "CONCAT(w1.nombre, ' ', w1.apellido) AS ganador1, " +
                            "CONCAT(COALESCE(w2.nombre,''), ' ', COALESCE(w2.apellido,'')) AS ganador2, " +
                            "CONCAT(l1.nombre, ' ', l1.apellido) AS perdedor1, " +
                            "CONCAT(COALESCE(l2.nombre,''), ' ', COALESCE(l2.apellido,'')) AS perdedor2, " +
                            "mr.winners_score, mr.losers_score, mr.created_at " +
                            "FROM king_of_court_match_result_db mr " +
                            "JOIN player_padel_db w1 ON mr.winner1_id = w1.id " +
                            "LEFT JOIN player_padel_db w2 ON mr.winner2_id = w2.id " +
                            "JOIN player_padel_db l1 ON mr.loser1_id = l1.id " +
                            "LEFT JOIN player_padel_db l2 ON mr.loser2_id = l2.id " +
                            "ORDER BY mr.id");

            // ─── 15. King of Court статистика игроков ──────────────
            addSheetFromSpan(workbook, "KingOfCourt_Stats", headerStyle, dataStyle, dateStyle,
                    "SELECT s.id, t.nombre AS torneo, CONCAT(p.nombre, ' ', p.apellido) AS jugador, " +
                            "s.total_points, s.bonus_points, s.games_played, s.wins, s.losses " +
                            "FROM king_of_court_player_stats_db s " +
                            "JOIN tournament_king_of_court_db k ON s.tournament_king_id = k.id " +
                            "JOIN tournaments_db t ON k.tournament_id = t.id " +
                            "JOIN player_padel_db p ON s.player_id = p.id " +
                            "ORDER BY s.total_points DESC");

            // ─── 16. Americano раунды ──────────────────────────────
            addSheetFromSpan(workbook, "Americano_Rondas", headerStyle, dataStyle, dateStyle,
                    "SELECT r.id, t.nombre AS torneo, r.round_number, r.status, " +
                            "r.points_per_match, r.is_doubles, r.courts, r.note, " +
                            "r.started_at, r.completed_at " +
                            "FROM americano_rounds_db r " +
                            "JOIN tournaments_db t ON r.tournament_id = t.id " +
                            "ORDER BY r.id");

            // ─── 17. Americano игроки ──────────────────────────────
            addSheetFromSpan(workbook, "Americano_Jugadores", headerStyle, dataStyle, dateStyle,
                    "SELECT ap.id, t.nombre AS torneo, " +
                            "CONCAT(p.nombre, ' ', p.apellido) AS jugador, " +
                            "ap.initial_position, ap.current_position, ap.status, " +
                            "ap.total_score, ap.matches_played, ap.matches_won, ap.matches_lost, ap.matches_drawn, " +
                            "ap.points_scored, ap.points_conceded, ap.bye_count, ap.last_bye_round, " +
                            "ap.unique_partners, ap.unique_opponents, " +
                            "ap.dropped_out_at, ap.dropped_out_reason " +
                            "FROM americano_players_db ap " +
                            "JOIN tournaments_db t ON ap.tournament_id = t.id " +
                            "JOIN player_padel_db p ON ap.player_id = p.id " +
                            "ORDER BY ap.total_score DESC");

            // ─── 18. Americano матчи ───────────────────────────────
            addSheetFromSpan(workbook, "Americano_Matches", headerStyle, dataStyle, dateStyle,
                    "SELECT m.id, t.nombre AS torneo, r.round_number, m.match_number, " +
                            "CONCAT(t1p1.nombre, ' ', t1p1.apellido) AS team1_j1, " +
                            "CONCAT(COALESCE(t1p2.nombre,''), ' ', COALESCE(t1p2.apellido,'')) AS team1_j2, " +
                            "CONCAT(t2p1.nombre, ' ', t2p1.apellido) AS team2_j1, " +
                            "CONCAT(COALESCE(t2p2.nombre,''), ' ', COALESCE(t2p2.apellido,'')) AS team2_j2, " +
                            "m.team1_score, m.team2_score, m.is_doubles, m.status, " +
                            "m.court_number, m.started_at, m.completed_at, m.note " +
                            "FROM americano_matches_db m " +
                            "JOIN americano_rounds_db r ON m.round_id = r.id " +
                            "JOIN tournaments_db t ON m.tournament_id = t.id " +
                            "LEFT JOIN player_padel_db t1p1 ON m.team1_player1_id = t1p1.id " +
                            "LEFT JOIN player_padel_db t1p2 ON m.team1_player2_id = t1p2.id " +
                            "LEFT JOIN player_padel_db t2p1 ON m.team2_player1_id = t2p1.id " +
                            "LEFT JOIN player_padel_db t2p2 ON m.team2_player2_id = t2p2.id " +
                            "ORDER BY m.id");

            // ─── 19. Americano команды ─────────────────────────────
            addSheetFromSpan(workbook, "Americano_Equipos", headerStyle, dataStyle, dateStyle,
                    "SELECT at.id, t.nombre AS torneo, at.team_number, " +
                            "CONCAT(p1.nombre, ' ', p1.apellido) AS jugador1, " +
                            "CASE WHEN at.player2_id IS NOT NULL " +
                            "  THEN CONCAT(p2.nombre, ' ', p2.apellido) " +
                            "  ELSE at.player2_name END AS jugador2, " +
                            "at.player2_phone, at.current_position, at.status, " +
                            "at.total_score, at.matches_played, at.matches_won, at.matches_lost, at.matches_drawn, " +
                            "at.points_scored, at.points_conceded " +
                            "FROM americano_teams_db at " +
                            "JOIN tournaments_db t ON at.tournament_id = t.id " +
                            "JOIN player_padel_db p1 ON at.player1_id = p1.id " +
                            "LEFT JOIN player_padel_db p2 ON at.player2_id = p2.id " +
                            "ORDER BY at.team_number");

            // ─── Информационный лист ───────────────────────────────
            addInfoSheet(workbook, timestamp, headerStyle, dataStyle);

            workbook.write(response.getOutputStream());
            response.flushBuffer();
            log.info("Экспорт данных завершён: {}", filename);
        }
    }

    // ─── Вспомогательные методы ────────────────────────────────────────

    private void addSheetFromSQL(Workbook workbook, String sheetName,
                                 CellStyle header, CellStyle data, CellStyle date,
                                 String tableName, String sql, List<String> headers) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            Sheet sheet = workbook.createSheet(sanitizeSheetName(sheetName));

            // Заголовки
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(header);
            }

            // Данные
            for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
                Row row = sheet.createRow(rowIdx + 1);
                Map<String, Object> rowData = rows.get(rowIdx);
                int colIdx = 0;
                for (Map.Entry<String, Object> entry : rowData.entrySet()) {
                    Cell cell = row.createCell(colIdx);
                    setCellValue(cell, entry.getValue(), data, date);
                    colIdx++;
                }
            }

            if (!rows.isEmpty()) {
                sheet.setAutoFilter(org.apache.poi.ss.util.CellRangeAddress.valueOf(
                        "A1:" + columnLetter(headers.size() - 1) + (rows.size() + 1)));
            }
            sheet.createFreezePane(0, 1);
            autoSizeColumns(sheet, headers.size());

            log.info("✓ {} — {} filas", sheetName, rows.size());
        } catch (Exception e) {
            log.warn("✗ {} — error: {}", sheetName, e.getMessage());
            addErrorSheet(workbook, sheetName, e.getMessage(), header, data);
        }
    }

    private void addSheetFromQuery(Workbook workbook, String sheetName,
                                   CellStyle header, CellStyle data, CellStyle date,
                                   String sql) {
        addSheetFromSpan(workbook, sheetName, header, data, date, sql);
    }

    private void addSheetFromSpan(Workbook workbook, String sheetName,
                                  CellStyle header, CellStyle data, CellStyle date,
                                  String sql) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

            if (rows.isEmpty()) {
                log.info("○ {} — vacío", sheetName);
                Sheet sheet = workbook.createSheet(sanitizeSheetName(sheetName));
                sheet.createRow(0).createCell(0).setCellValue("Sin datos");
                return;
            }

            Sheet sheet = workbook.createSheet(sanitizeSheetName(sheetName));

            // Берём имена колонок из первого ряда
            Map<String, Object> firstRow = rows.get(0);
            List<String> columnNames = new ArrayList<>(firstRow.keySet());

            // Красивые заголовки (заменяем snake_case на Title Case)
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columnNames.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(beautifyColumnName(columnNames.get(i)));
                cell.setCellStyle(header);
            }

            // Данные
            for (int rowIdx = 0; rowIdx < rows.size(); rowIdx++) {
                Row row = sheet.createRow(rowIdx + 1);
                Map<String, Object> rowData = rows.get(rowIdx);
                for (int colIdx = 0; colIdx < columnNames.size(); colIdx++) {
                    Cell cell = row.createCell(colIdx);
                    setCellValue(cell, rowData.get(columnNames.get(colIdx)), data, date);
                }
            }

            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                    0, rows.size(), 0, columnNames.size() - 1));
            sheet.createFreezePane(0, 1);
            autoSizeColumns(sheet, columnNames.size());

            log.info("✓ {} — {} filas", sheetName, rows.size());
        } catch (Exception e) {
            log.warn("✗ {} — error: {}", e.getMessage());
            addErrorSheet(workbook, sheetName, e.getMessage(), header, data);
        }
    }

    private void setCellValue(Cell cell, Object value, CellStyle dataStyle, CellStyle dateStyle) {
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof java.sql.Timestamp || value instanceof java.time.LocalDateTime) {
            String str = value.toString().replace("T", " ");
            if (str.length() > 19) str = str.substring(0, 19);
            cell.setCellValue(str);
            cell.setCellStyle(dateStyle);
        } else if (value instanceof java.sql.Date || value instanceof java.time.LocalDate) {
            cell.setCellValue(value.toString());
            cell.setCellStyle(dateStyle);
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
            cell.setCellStyle(dataStyle);
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value ? "Sí" : "No");
            cell.setCellStyle(dataStyle);
        } else {
            String str = value.toString();
            if (str.length() > 32767) str = str.substring(0, 32767) + "...";
            cell.setCellValue(str);
            cell.setCellStyle(dataStyle);
        }
    }

    private void addErrorSheet(Workbook workbook, String name, String error,
                               CellStyle header, CellStyle data) {
        Sheet sheet = workbook.createSheet(sanitizeSheetName(name + "_error"));
        Row r0 = sheet.createRow(0);
        r0.createCell(0).setCellValue("Error");
        r0.getCell(0).setCellStyle(header);
        Row r1 = sheet.createRow(1);
        r1.createCell(0).setCellValue(error);
        r1.getCell(0).setCellStyle(data);
        sheet.autoSizeColumn(0);
    }

    private void addInfoSheet(Workbook workbook, String timestamp,
                              CellStyle header, CellStyle data) {
        Sheet sheet = workbook.createSheet("Info");
        String[][] info = {
                {"Fecha exportación", timestamp},
                {"Aplicación", "Padel Core Service"},
                {"Columnas", "Todos los IDs están reemplazados por nombres"},
                {"Formato fechas", "yyyy-MM-dd HH:mm:ss"}
        };
        for (int i = 0; i < info.length; i++) {
            Row row = sheet.createRow(i);
            Cell c0 = row.createCell(0);
            c0.setCellValue(info[i][0]);
            c0.setCellStyle(header);
            Cell c1 = row.createCell(1);
            c1.setCellValue(info[i][1]);
            c1.setCellStyle(data);
        }
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private String beautifyColumnName(String snakeCase) {
        // tournament_id → Torneo ID
        // player_name → Jugador Nombre
        // is_active → Activo
        String[] parts = snakeCase.split("_");
        StringBuilder result = new StringBuilder();

        // Специальные префиксы
        Map<String, String> prefixes = Map.of(
                "club", "Club",
                "player", "Jugador",
                "team1", "Equipo1",
                "team2", "Equipo2",
                "winner", "Ganador",
                "loser", "Perdedor"
        );

        for (int i = 0; i < parts.length; i++) {
            if (i > 0) result.append(" ");
            String part = parts[i];

            if (i == 0 && prefixes.containsKey(part)) {
                result.append(prefixes.get(part));
            } else if (part.equals("id")) {
                result.append("ID");
            } else if (part.equals("is")) {
                // is_active → Activo (пропускаем "is")
                continue;
            } else if (part.length() <= 3 && part.equals(part.toUpperCase())) {
                result.append(part);
            } else {
                result.append(part.substring(0, 1).toUpperCase())
                        .append(part.substring(1).toLowerCase());
            }
        }
        return result.toString().trim();
    }

    private void autoSizeColumns(Sheet sheet, int colCount) {
        for (int i = 0; i < colCount; i++) {
            sheet.autoSizeColumn(i, true);
            int width = Math.min(sheet.getColumnWidth(i), 50 * 256);
            sheet.setColumnWidth(i, Math.max(width, 10 * 256));
        }
    }

    private String columnLetter(int index) {
        StringBuilder sb = new StringBuilder();
        while (index >= 0) {
            sb.insert(0, (char) ('A' + index % 26));
            index = index / 26 - 1;
        }
        return sb.toString();
    }

    private String sanitizeSheetName(String name) {
        String sanitized = name.replaceAll("[\\\\/*\\[\\]:?]", "_");
        return sanitized.length() > 31 ? sanitized.substring(0, 31) : sanitized;
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        addBorders(style);
        return style;
    }

    private CellStyle createDataStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        addBorders(style);
        return style;
    }

    private CellStyle createDateStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        addBorders(style);
        style.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-MM-dd HH:mm:ss"));
        return style;
    }

    private void addBorders(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}