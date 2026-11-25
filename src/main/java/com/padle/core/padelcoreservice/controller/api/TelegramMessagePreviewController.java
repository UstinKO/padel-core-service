package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.TelegramMessagePreviewService;
import com.padle.core.padelcoreservice.telegram.TelegramAudienceModels.*;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/telegram/messages")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN','ORGANIZER','ADMIN','CLUB_ADMIN')")
public class TelegramMessagePreviewController {
    private final TelegramMessagePreviewService messages;

    @ModelAttribute
    public void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    @GetMapping("/audience")
    public Audience audience(@AuthenticationPrincipal Owner owner, @RequestParam(required = false) Long tournamentId) {
        return messages.audience(owner, tournamentId);
    }

    @PostMapping("/preview")
    public Preview preview(@AuthenticationPrincipal Owner owner, @Valid @RequestBody Selection selection) {
        return messages.preview(owner, selection);
    }
}
