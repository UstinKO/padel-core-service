package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.calendar.PublicCalendarFeed.Personal;
import com.padle.core.padelcoreservice.service.AccountService;
import com.padle.core.padelcoreservice.service.calendar.PublicCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('PLAYER')")
public class PersonalCalendarController {
    private final PublicCalendarService calendar;
    private final AccountService accounts;

    @GetMapping("/api/players/me/calendar")
    public ResponseEntity<Personal> personal(Authentication authentication, @RequestParam(required = false) String month) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, "Cookie, Authorization")
                .body(calendar.personal(accounts.player(authentication), month));
    }
}
