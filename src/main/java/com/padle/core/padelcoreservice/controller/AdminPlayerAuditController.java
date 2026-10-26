package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.dto.DuplicatesAuditReportDto;
import com.padle.core.padelcoreservice.service.PlayerDuplicatesAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Отчёт о потенциальных дублях игроков — только чтение, ничего не меняет.
 */
@RestController
@RequestMapping("/api/admin/players")
@RequiredArgsConstructor
@Slf4j
public class AdminPlayerAuditController {

    private final PlayerDuplicatesAuditService playerDuplicatesAuditService;

    @GetMapping("/duplicates-audit")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<DuplicatesAuditReportDto> getDuplicatesAudit() {
        log.info("Запрошен аудит дублей игроков");
        return ResponseEntity.ok(playerDuplicatesAuditService.buildReport());
    }
}
