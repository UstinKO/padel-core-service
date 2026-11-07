package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PairReplacementNoticeService {
    private final JdbcTemplate jdbc;

    public record Notice(Long id, String tournamentName, String bookerName, String replacementName) {}

    public Notice record(TournamentDto tournament, PlayerPadel removed, PlayerPadel booker, PlayerPadel replacement) {
        String bookerName = booker.getNombreCompleto();
        String replacementName = replacement.getNombreCompleto();
        Long id = jdbc.queryForObject("""
                INSERT INTO pair_replacement_notices_db (player_id, tournament_id, tournament_name, booker_name, replacement_name)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, removed.getId(), tournament.getId(), tournament.getNombre(), bookerName, replacementName);
        return new Notice(id, tournament.getNombre(), bookerName, replacementName);
    }

    public List<Notice> unread(Long playerId) {
        return jdbc.query("""
                SELECT id, tournament_name, booker_name, replacement_name
                FROM pair_replacement_notices_db
                WHERE player_id = ? AND dismissed_at IS NULL
                ORDER BY id DESC LIMIT 20
                """, (row, i) -> new Notice(row.getLong("id"), row.getString("tournament_name"),
                row.getString("booker_name"), row.getString("replacement_name")), playerId);
    }

    public void dismiss(Long playerId, Long noticeId) {
        jdbc.update("""
                UPDATE pair_replacement_notices_db SET dismissed_at = CURRENT_TIMESTAMP
                WHERE id = ? AND player_id = ? AND dismissed_at IS NULL
                """, noticeId, playerId);
    }
}
