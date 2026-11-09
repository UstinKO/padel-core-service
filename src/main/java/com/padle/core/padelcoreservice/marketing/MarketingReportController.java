package com.padle.core.padelcoreservice.marketing;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MarketingReportController {
    private final TournamentAccessService access;
    private final MarketingReportService report;

    @GetMapping("/admin/marketing/export.csv")
    public ResponseEntity<String> export(@RequestParam long tournamentId, @AuthenticationPrincipal Owner owner) {
        access.assertCanManageTournament(owner, tournamentId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=marketing.csv")
                .body(report.export(tournamentId));
    }
}
