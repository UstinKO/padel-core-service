package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.PublicEventResponse;
import com.padle.core.padelcoreservice.service.PublicEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/events")
@RequiredArgsConstructor
public class PublicEventController {
    private final PublicEventService service;
    @GetMapping("/{id}")
    public ResponseEntity<PublicEventResponse> details(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.event(id));
    }
}
