package com.padle.core.padelcoreservice.repository.marketing;

import com.padle.core.padelcoreservice.service.marketing.QrStats;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class QrStatsRepository {
    private final JdbcTemplate jdbc;

    public void increment(LocalDate date, String destination) {
        jdbc.update("""
                INSERT INTO marketing_qr_daily_db (fecha, origen, destino, aperturas)
                VALUES (?, 'reby', ?, 1)
                ON CONFLICT (fecha, origen, destino)
                DO UPDATE SET aperturas = marketing_qr_daily_db.aperturas + 1
                """, Date.valueOf(date), destination);
    }

    public List<QrStats.Day> findDays(LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT fecha,
                       SUM(CASE WHEN destino = 'site' THEN aperturas ELSE 0 END) AS site,
                       SUM(CASE WHEN destino = 'telegram' THEN aperturas ELSE 0 END) AS telegram
                FROM marketing_qr_daily_db
                WHERE origen = 'reby' AND fecha BETWEEN ? AND ?
                GROUP BY fecha ORDER BY fecha
                """, (rs, row) -> new QrStats.Day(rs.getDate("fecha").toLocalDate(),
                        rs.getLong("site"), rs.getLong("telegram")),
                Date.valueOf(from), Date.valueOf(to));
    }
}
