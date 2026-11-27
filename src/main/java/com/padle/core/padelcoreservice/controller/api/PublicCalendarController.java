package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.calendar.PublicCalendarFeed;
import com.padle.core.padelcoreservice.dto.calendar.PublicCalendarFilter;
import com.padle.core.padelcoreservice.model.enums.EventFormat;
import com.padle.core.application.tournament.GenderFormat;
import com.padle.core.application.tournament.Nivel;
import com.padle.core.padelcoreservice.service.calendar.PublicCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/calendar")
@RequiredArgsConstructor
public class PublicCalendarController {
    private final PublicCalendarService calendar;

    @ModelAttribute("filters")
    public PublicCalendarFilter filters(@RequestParam(required = false) Long clubId,
            @RequestParam(required = false) Long organizerId, @RequestParam(required = false) EventFormat format,
            @RequestParam(required = false) GenderFormat gender, @RequestParam(required = false) Nivel level) {
        return new PublicCalendarFilter(clubId, organizerId, format, gender, level);
    }

    @GetMapping
    public ResponseEntity<PublicCalendarFeed> month(@RequestParam(required = false) String month,
            @ModelAttribute("filters") PublicCalendarFilter filters) {
        return response(calendar.month(month, filters));
    }

    @GetMapping("/catalog")
    public ResponseEntity<PublicCalendarFeed> catalog(@ModelAttribute("filters") PublicCalendarFilter filters) {
        return response(calendar.catalog(filters));
    }

    private static ResponseEntity<PublicCalendarFeed> response(PublicCalendarFeed body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
