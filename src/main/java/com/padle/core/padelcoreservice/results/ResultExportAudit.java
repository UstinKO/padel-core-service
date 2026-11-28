package com.padle.core.padelcoreservice.results;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResultExportAudit {
    private final JdbcTemplate jdbc;

    @Transactional
    public void generated(String actor, ResultReport report, ResultFiles.Format format) {
        jdbc.update("""
                INSERT INTO tournament_result_exports (actor,tournament_id,file_format,contacts,participant_count,match_count)
                VALUES (?,?,?,?,?,?)
                """,actor,report.tournament().id(),format.name(),report.contacts(),report.participants().size(),report.matches().size());
    }
}
