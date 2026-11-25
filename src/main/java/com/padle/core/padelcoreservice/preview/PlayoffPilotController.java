package com.padle.core.padelcoreservice.preview;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("preview")
@RequiredArgsConstructor
@RequestMapping("/api/internal/ui-preview/playoff-pilot")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlayoffPilotController {
    private final PlayoffPilotFixtureService fixtures;

    @GetMapping
    public ResponseEntity<PlayoffPilotFixtureService.Fixtures> status() {
        return fixtures.snapshot().map(this::response).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<PlayoffPilotFixtureService.Fixtures> seed() {
        return response(fixtures.seed());
    }

    @PostMapping("/reset")
    public ResponseEntity<PlayoffPilotFixtureService.Fixtures> reset() {
        return response(fixtures.reset());
    }

    private ResponseEntity<PlayoffPilotFixtureService.Fixtures> response(PlayoffPilotFixtureService.Fixtures state) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(state);
    }
}
