package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.model.enums.EventFormat;
import com.padle.core.padelcoreservice.dto.calendar.AdminCalendarMonth;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.Nivel;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.service.calendar.AdminCalendarService;
import com.padle.core.padelcoreservice.service.calendar.EventSchedule;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.YearMonth;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin/events/calendar")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'OWNER', 'ORGANIZER', 'CLUB_ADMIN')")
public class AdminCalendarController {
    private final AdminCalendarService calendar;

    @GetMapping
    public ResponseEntity<AdminCalendarMonth> month(@AuthenticationPrincipal Owner owner,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) Long clubId,
            @RequestParam(required = false) EventFormat format,
            @RequestParam(required = false) GenderFormat gender,
            @RequestParam(required = false) Nivel level,
            @RequestParam(required = false) TournamentStatus status,
            @RequestParam(required = false) Long organizerId) {
        YearMonth selected = Optional.ofNullable(month).map(YearMonth::parse)
                .orElseGet(() -> YearMonth.now(EventSchedule.zone(EventSchedule.DEFAULT_ZONE)));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(calendar.month(owner, selected,
                new AdminCalendarService.Filters(clubId, format, gender, level, status, organizerId)));
    }
}
