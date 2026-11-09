package com.padle.core.padelcoreservice.service.calendar;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.enums.Modalidad;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Duration;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class TournamentCalendarService {
    private final TournamentService tournamentService;
    private final MessageSource messages;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public CalendarEvent getEvent(Long id, Locale locale) {
        TournamentDto event = tournamentService.getActiveTournamentById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        try {
            if (event.getFechaInicio() == null || event.getHoraInicio() == null || event.getTipo() == null) {
                throw new IllegalArgumentException("calendar.unavailable");
            }
            Duration duration = EventSchedule.duration(event)
                    .orElseThrow(() -> new IllegalArgumentException("calendar.unavailable"));
            ZonedDateTime start = EventSchedule.start(event);
            String title = title(event, locale);
            String url = publicUrl(id);
            String description = String.join("\n", title, message("calendar.arrival", locale),
                    message("calendar.updates", locale), url);
            Instant updated = Optional.ofNullable(event.getUpdatedAt()).or(() -> Optional.ofNullable(event.getCreatedAt()))
                    .map(date -> date.atZone(start.getZone()).toInstant()).orElse(start.toInstant());
            return new CalendarEvent("tournament-" + id + "@1-padel.com", title, start.toInstant(),
                    start.plus(duration).toInstant(), start.getZone().getId(), location(event), description, url, updated);
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, message("calendar.unavailable", locale));
        }
    }

    private String title(TournamentDto event, Locale locale) {
        String format = message("calendar.format." + event.getTipo().name(), locale);
        boolean openFormat = event.getTipo() == TournamentType.CANCHA_ABIERTA || event.getTipo() == TournamentType.PADEL_CLINIC;
        String category = openFormat ? "" : Optional.ofNullable(event.getCategoriaNivel())
                .filter(level -> !level.isBlank())
                .map(level -> level.matches("[CD][4-9](?:_[CD][4-9])?") ? level.replace('_', '/')
                        : levelLabel(level, locale))
                .map(level -> " " + level).orElse("");
        String pairs = !openFormat && event.getModalidad() == Modalidad.DOBLES ? " · " + message("calendar.pairs", locale) : "";
        return format + category + pairs + " — 1-Padel";
    }

    private String levelLabel(String level, Locale locale) {
        if (level.startsWith("SUMA_")) {
            return messages.getMessage("calendar.level.SUMA", new Object[]{level.substring(5)}, locale);
        }
        return messages.getMessage("calendar.level." + level, null, level.replace('_', ' '), locale);
    }

    private String location(TournamentDto event) {
        return Stream.of(event.getClubNombre(), event.getClubDireccion()).filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(", "));
    }

    private String publicUrl(Long id) {
        URI base = URI.create(baseUrl);
        return base.resolve("/torneo/" + id).toASCIIString();
    }

    private String message(String key, Locale locale) {
        return messages.getMessage(key, null, locale);
    }
}
