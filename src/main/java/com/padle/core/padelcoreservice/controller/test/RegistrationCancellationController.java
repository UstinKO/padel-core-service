package com.padle.core.padelcoreservice.controller.test;

import com.padle.core.padelcoreservice.service.TournamentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/test/tournaments")
@RequiredArgsConstructor
@Slf4j
public class RegistrationCancellationController {
    private final TournamentService tournamentService;
    private final MessageSource messageSource;

    public record CancellationResponse(boolean success, String message) {
    }

    @PostMapping("/{tournamentId}/remove-player/{playerId}")
    public CancellationResponse cancel(@PathVariable Long tournamentId, @PathVariable Long playerId,
                                       Locale locale) {
        tournamentService.adminRemovePlayerRegistration(tournamentId, playerId);
        return new CancellationResponse(true,
                messageSource.getMessage("registration.cancel.success", null, locale));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CancellationResponse> failed(Exception exception, Locale locale) {
        log.error("Не удалось отменить участие в мероприятии", exception);
        return ResponseEntity.internalServerError().body(new CancellationResponse(false,
                messageSource.getMessage("registration.cancel.failure", null, locale)));
    }
}
