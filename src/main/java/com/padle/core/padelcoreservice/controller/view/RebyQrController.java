package com.padle.core.padelcoreservice.controller.view;

import com.padle.core.padelcoreservice.service.marketing.QrRequestPolicy;
import com.padle.core.padelcoreservice.service.marketing.QrStats;
import com.padle.core.padelcoreservice.service.marketing.QrStatsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class RebyQrController {
    private static final Map<String, String> TARGETS = Map.of(
            "site", "https://1-padel.com/?utm_source=reby&utm_medium=qr&utm_campaign=offline_tournaments",
            "telegram", "https://t.me/padelarg");
    private final QrStatsService stats;

    @GetMapping("/qr/reby/site")
    public ResponseEntity<Void> site(HttpServletRequest request) {
        return redirect("site", request);
    }

    @GetMapping("/qr/reby/telegram")
    public ResponseEntity<Void> telegram(HttpServletRequest request) {
        return redirect("telegram", request);
    }

    private ResponseEntity<Void> redirect(String destination, HttpServletRequest request) {
        if (QrRequestPolicy.shouldCount(request.getMethod(), request.getHeader("User-Agent"),
                request.getHeader("Purpose"), request.getHeader("Sec-Purpose"))) {
            stats.recordClick(destination);
        }
        return ResponseEntity.status(HttpStatus.FOUND).cacheControl(CacheControl.noStore())
                .location(URI.create(TARGETS.get(destination))).build();
    }

    @GetMapping("/api/admin/marketing/qr-stats")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<QrStats> report(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(stats.stats(from, to));
    }
}
