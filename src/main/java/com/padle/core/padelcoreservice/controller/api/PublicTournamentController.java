package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.annotation.Timed;
import com.padle.core.padelcoreservice.dto.PublicTournamentDto;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.mapper.PublicTournamentMapper;
import com.padle.core.padelcoreservice.service.TournamentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * LFPT-0623: публичное (анонимное) чтение расписания турниров для мобильного клиента.
 * Переиспользует {@code TournamentService.getActiveTournamentsForHome()}/{@code
 * getActiveTournamentById()} — те же методы и правила видимости, что у публичных страниц
 * {@code /} и {@code /torneo/{id}}. Только чтение: мутации остаются под авторизацией.
 */
@RestController
@RequestMapping("/api/public/tournaments")
@RequiredArgsConstructor
public class PublicTournamentController {
    private final TournamentService tournamentService;
    private final PublicTournamentMapper mapper;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @GetMapping
    @Timed(name = "public.schedule.list.time", tags = {"service=public-schedule", "operation=list"})
    public ResponseEntity<List<PublicTournamentDto>> list() {
        List<PublicTournamentDto> tournaments = tournamentService.getActiveTournamentsForHome().stream()
                .map(tournament -> mapper.toPublicDto(tournament, baseUrl))
                .toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tournaments);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PublicTournamentDto> details(@PathVariable Long id) {
        PublicTournamentDto tournament = tournamentService.getActiveTournamentById(id)
                .map(value -> mapper.toPublicDto(value, baseUrl))
                .orElseThrow(() -> new ResourceNotFoundException("TOURNAMENT_NOT_FOUND"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(tournament);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("TOURNAMENT_NOT_FOUND"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> invalidId() {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_TOURNAMENT_ID"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError("SCHEDULE_UNAVAILABLE"));
    }

    public record ApiError(String code) {}
}
