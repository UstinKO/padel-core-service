package com.padle.core.padelcoreservice.controller;

import com.padle.core.padelcoreservice.annotation.Counted;
import com.padle.core.padelcoreservice.annotation.Timed;
import com.padle.core.padelcoreservice.metrics.EmailMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminMetricsController {

    private final EmailMetricsService emailMetricsService;

    @GetMapping("/email-stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    @Timed(name = "admin.email.stats.time", description = "Time to get email stats")
    @Counted(name = "admin.email.stats.access", description = "Email stats access count")
    public ResponseEntity<Map<String, Object>> getEmailStats() {
        log.info("Fetching email statistics");
        return ResponseEntity.ok(emailMetricsService.getEmailStats());
    }

    @GetMapping("/email-daily-count")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORGANIZER')")
    @Timed(name = "admin.email.daily.count.time")
    public ResponseEntity<Map<String, Object>> getDailyEmailCount() {
        Map<String, Object> response = Map.of(
                "dailyCount", emailMetricsService.getDailyEmailCount(),
                "dailyLimit", emailMetricsService.getDailyLimit(),
                "remainingQuota", emailMetricsService.getDailyLimit() - emailMetricsService.getDailyEmailCount(),
                "usagePercentage", emailMetricsService.getUsagePercentage()
        );
        return ResponseEntity.ok(response);
    }
}