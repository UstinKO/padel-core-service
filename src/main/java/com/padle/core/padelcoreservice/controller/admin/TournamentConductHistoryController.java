package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.padle.core.padelcoreservice.service.conduct.TournamentConductHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/tournaments/{tournamentId}/conduct-history")
@RequiredArgsConstructor
@Slf4j
public class TournamentConductHistoryController {

    private final TournamentConductHistory history;
    private final TournamentService tournaments;

    @GetMapping
    public String view(@PathVariable long tournamentId, @RequestParam(required = false) Long version,
                       @AuthenticationPrincipal Owner owner, Model model) {
        var versions = history.list(tournamentId, owner);
        model.addAttribute("versions", versions);
        model.addAttribute("tournament", tournaments.getTournamentDtoById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament not found")));
        if (version != null) {
            var preview = history.preview(tournamentId, version, owner);
            model.addAttribute("preview", preview);
            model.addAttribute("laterVersions", versions.stream().filter(item -> item.id() > version).toList());
        }
        return "admin/tournaments/conduct-history";
    }

    @PostMapping("/{versionId}/restore")
    public String restore(@PathVariable long tournamentId, @PathVariable long versionId,
                          @RequestParam long expectedVersion, @AuthenticationPrincipal Owner owner,
                          RedirectAttributes redirect) {
        try {
            history.restore(tournamentId, versionId, expectedVersion, owner);
            redirect.addFlashAttribute("conductMessage", "conduct.history.restored");
        } catch (InvalidStateException e) {
            redirect.addFlashAttribute("conductError", e.getMessage());
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("Tournament {} restore failed for version {}", tournamentId, versionId, e);
            redirect.addFlashAttribute("conductError", "conduct.save.error");
        }
        return "redirect:/admin/tournaments/" + tournamentId + "/conduct-history";
    }
}
