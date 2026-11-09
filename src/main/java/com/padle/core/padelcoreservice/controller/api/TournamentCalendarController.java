package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.service.calendar.CalendarEvent;
import com.padle.core.padelcoreservice.service.calendar.CalendarEventWriter;
import com.padle.core.padelcoreservice.service.calendar.TournamentCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/public/tournaments/{id}/calendar")
@RequiredArgsConstructor
public class TournamentCalendarController {
    private final TournamentCalendarService calendarService;

    @GetMapping
    public ResponseEntity<CalendarLinks> links(@PathVariable Long id, @RequestParam(required = false) String lang, Locale locale) {
        CalendarEvent event = calendarService.getEvent(id, language(lang, locale));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new CalendarLinks(CalendarEventWriter.googleUrl(event)));
    }

    @GetMapping("/google")
    public ResponseEntity<Void> google(@PathVariable Long id, @RequestParam(required = false) String lang, Locale locale) {
        CalendarEvent event = calendarService.getEvent(id, language(lang, locale));
        return ResponseEntity.status(302).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.LOCATION, CalendarEventWriter.googleUrl(event)).build();
    }

    @GetMapping(value = "/event.ics", produces = "text/calendar;charset=UTF-8")
    public ResponseEntity<byte[]> file(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean download,
                                       @RequestParam(required = false) String lang, Locale locale) {
        CalendarEvent event = calendarService.getEvent(id, language(lang, locale));
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename("1-padel-" + id + ".ics").build();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("text/calendar;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(CalendarEventWriter.iCalendar(event));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<CalendarError> error(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CalendarError(exception.getReason()));
    }

    public record CalendarError(String message) {}

    private Locale language(String lang, Locale locale) {
        String requested = lang == null ? locale.getLanguage() : lang;
        return Locale.forLanguageTag(Set.of("ru", "es", "en").contains(requested) ? requested : "es");
    }

    public record CalendarLinks(String googleUrl) {}
}
