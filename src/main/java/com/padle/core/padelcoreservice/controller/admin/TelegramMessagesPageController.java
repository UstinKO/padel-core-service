package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@ConditionalOnResource(resources = "classpath:templates/admin/telegram-messages.html")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN','ORGANIZER','ADMIN','CLUB_ADMIN')")
public class TelegramMessagesPageController {
    private final TournamentAccessService access;

    @GetMapping("/admin/telegram/messages")
    public String global(@AuthenticationPrincipal Owner owner, Model model) {
        if (!owner.isSuperAdmin()) {
            throw new AccessDeniedException("Общая аудитория доступна главному администратору");
        }
        model.addAttribute("audienceTournamentId", "");
        return "admin/telegram-messages";
    }

    @GetMapping("/admin/tournaments/{id}/telegram/messages")
    public String event(@PathVariable long id, @AuthenticationPrincipal Owner owner, Model model) {
        access.assertCanManageTournament(owner, id);
        model.addAttribute("audienceTournamentId", id);
        return "admin/telegram-messages";
    }
}
