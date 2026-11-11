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
import org.springframework.web.bind.annotation.RequestParam;
import com.padle.core.padelcoreservice.exception.TournamentRegistrationException;
import org.springframework.security.access.AccessDeniedException;

import java.util.Locale;
import com.padle.core.padelcoreservice.model.Owner;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

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
                                       @AuthenticationPrincipal Owner owner, @RequestParam(required=false) String comment, Locale locale) {
        if(comment==null) tournamentService.adminRemovePlayerRegistration(tournamentId, playerId, owner);
        else tournamentService.adminRemovePlayerRegistration(tournamentId, playerId, owner,comment);
        return new CancellationResponse(true,
                messageSource.getMessage("registration.cancel.success", null, locale));
    }

    @ExceptionHandler(TournamentRegistrationException.class)
    public ResponseEntity<CancellationResponse> invalid(TournamentRegistrationException exception) {
        return ResponseEntity.badRequest().body(new CancellationResponse(false,exception.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<CancellationResponse> forbidden(Locale locale) {
        return ResponseEntity.status(403).body(new CancellationResponse(false,
                messageSource.getMessage("registration.cancellation.forbidden",null,locale)));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CancellationResponse> failed(Exception exception, Locale locale) {
        log.warn("Не удалось отменить участие в мероприятии: type={}", exception.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(new CancellationResponse(false,
                messageSource.getMessage("registration.cancel.failure", null, locale)));
    }
}
