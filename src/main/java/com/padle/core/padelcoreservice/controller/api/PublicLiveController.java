package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.live.PublicLiveSnapshot;
import com.padle.core.padelcoreservice.service.PublicLiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicLiveController {
    private final PublicLiveService live;
    @GetMapping("/events/{eventId}/live")
    public ResponseEntity<PublicLiveSnapshot> snapshot(@PathVariable long eventId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(live.snapshot(eventId));
    }
    @GetMapping("/king-of-court/{kingId}/live")
    public ResponseEntity<PublicLiveSnapshot> king(@PathVariable long kingId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(live.kingAlias(kingId));
    }
}
