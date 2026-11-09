package com.padle.core.padelcoreservice.service;

import io.micrometer.core.annotation.Timed;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class TournamentAuditExportService {

    private final JdbcTemplate jdbcTemplate;
    private final String exportQuery;

    public TournamentAuditExportService(JdbcTemplate jdbcTemplate) throws IOException {
        this.jdbcTemplate = jdbcTemplate;
        this.exportQuery = new ClassPathResource("sql/tournament-audit-export.sql")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    @Timed("admin.tournaments.audit.export.time")
    @Transactional(readOnly = true, timeout = 30)
    public String exportJson() {
        return jdbcTemplate.queryForObject(exportQuery, String.class);
    }
}
