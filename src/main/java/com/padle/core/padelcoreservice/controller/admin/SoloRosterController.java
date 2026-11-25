package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.dto.SoloRoster.*;
import com.padle.core.padelcoreservice.exception.SoloRosterException;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.SoloRosterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/admin/tournaments/{eventId}/participants")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','ORGANIZER','CLUB_ADMIN')")
@RequiredArgsConstructor
public class SoloRosterController {
    private final SoloRosterService service;
    private final MessageSource messages;

    @GetMapping
    public ResponseEntity<Board> board(@PathVariable long eventId, @AuthenticationPrincipal Owner owner) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.board(eventId, owner));
    }

    @GetMapping("/search")
    public ResponseEntity<List<Found>> search(@PathVariable long eventId, @AuthenticationPrincipal Owner owner,
                                             @RequestParam(defaultValue = "") String q) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.search(eventId, owner, q));
    }

    @PostMapping("/preview")
    public Preview preview(@PathVariable long eventId, @AuthenticationPrincipal Owner owner,
                           @RequestBody @Valid Command command) {
        return service.preview(eventId, owner, command);
    }

    @PostMapping
    public Result apply(@PathVariable long eventId, @AuthenticationPrincipal Owner owner,
                        @RequestBody @Valid Command command) {
        return service.apply(eventId, owner, command);
    }

    @ExceptionHandler(SoloRosterException.class)
    public ResponseEntity<ProblemDetail> problem(SoloRosterException error, Locale locale) {
        var status = error.getMessage().equals("solo.error.event") ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
        var problem = ProblemDetail.forStatusAndDetail(status, messages.getMessage(error.getMessage(), error.arguments(), locale));
        problem.setProperty("code", error.getMessage());
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> forbidden(Locale locale) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                messages.getMessage("solo.error.forbidden", null, locale)));
    }
}
