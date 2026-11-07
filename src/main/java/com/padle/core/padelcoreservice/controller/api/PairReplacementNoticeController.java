package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.service.PairReplacementNoticeService;
import com.padle.core.padelcoreservice.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/players/me/pair-replacement-notices")
public class PairReplacementNoticeController {
    private final PairReplacementNoticeService notices;

    @GetMapping
    public ResponseEntity<List<PairReplacementNoticeService.Notice>> unread(@AuthenticationPrincipal Object principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(notices.unread(playerId(principal)));
    }

    @PostMapping("/{noticeId}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@PathVariable Long noticeId, @AuthenticationPrincipal Object principal) {
        notices.dismiss(playerId(principal), noticeId);
    }

    private Long playerId(Object principal) {
        var player = SecurityUtils.extractPlayer(principal);
        if (player == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return player.getId();
    }
}
