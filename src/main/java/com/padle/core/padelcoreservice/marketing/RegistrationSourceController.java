package com.padle.core.padelcoreservice.marketing;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class RegistrationSourceController {
    private final RegistrationSourceReportService reports;

    @GetMapping("/api/admin/marketing/registration-sources")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<RegistrationSourceReport> report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "last_non_direct") String model,
            @RequestParam(defaultValue = "") String source, @RequestParam(defaultValue = "") String placement,
            @RequestParam(defaultValue = "") String medium, @RequestParam(defaultValue = "") String campaign,
            @RequestParam(defaultValue = "") String content, @RequestParam(defaultValue = "") String term,
            @RequestParam(required = false) Long tournamentId, @RequestParam(required = false) Long clubId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int pageSize) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reports.report(
                new RegistrationSourceReportService.Filter(from, to, model, source, placement, medium,
                        campaign, content, term, tournamentId, clubId, page, pageSize)));
    }
}
