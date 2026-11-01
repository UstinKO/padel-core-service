package com.padle.core.padelcoreservice.marketing;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/marketing")
@RequiredArgsConstructor
public class MarketingController {
    private final MetaProperties properties;
    private final MarketingContextService context;
    private final MarketingOutbox outbox;

    public record Config(boolean enabled, String pixelId, boolean testMode) { }
    public record Acknowledge(UUID eventId) { }

    @GetMapping("/config")
    public ResponseEntity<Config> config() {
        boolean enabled = properties.browserEnabled();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Config(enabled, enabled ? properties.pixelId() : "", properties.testMode()));
    }

    @PostMapping("/context")
    public ResponseEntity<MarketingContextService.Identity> capture(@RequestBody MarketingContextService.Capture capture,
                                       HttpServletRequest request, HttpServletResponse response) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(context.capture(capture, request, response));
    }

    @GetMapping("/events")
    public ResponseEntity<List<MarketingOutbox.BrowserEvent>> events(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(outbox.browserEvents(request));
    }

    @PostMapping("/ack")
    public ResponseEntity<Void> acknowledge(@RequestBody Acknowledge acknowledgement, HttpServletRequest request) {
        outbox.acknowledge(acknowledgement.eventId(), request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
