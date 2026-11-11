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
@RequestMapping("/api/internal/ui-preview")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PreviewController {
    private final PreviewFixtureService fixtures;
    private final PreviewProperties properties;
    public record Status(String environment, String revision, PreviewFixtureService.Fixtures fixtures) { }

    @GetMapping
    public ResponseEntity<Status> status() {
        return response(fixtures.snapshot());
    }

    @PostMapping("/reset")
    public ResponseEntity<Status> reset() {
        return response(fixtures.reset());
    }

    private ResponseEntity<Status> response(PreviewFixtureService.Fixtures snapshot) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Status("preview", properties.revision(), snapshot));
    }
}
