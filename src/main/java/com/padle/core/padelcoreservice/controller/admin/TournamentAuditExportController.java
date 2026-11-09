package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.service.TournamentAuditExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
public class TournamentAuditExportController {

    private final TournamentAuditExportService exportService;

    @GetMapping(value = "/admin/export/tournament-audit", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<byte[]> download() {
        var disposition = ContentDisposition.attachment().filename("1padel-tournament-audit.json").build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(exportService.exportJson().getBytes(StandardCharsets.UTF_8));
    }
}
