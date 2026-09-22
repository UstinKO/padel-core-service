package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.dto.BracketMatchDto;
import com.padle.core.padelcoreservice.dto.MatchDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.service.BracketService;
import com.padle.core.padelcoreservice.service.MatchService;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import com.padle.core.padelcoreservice.service.TournamentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/tournaments/{tournamentId}/matches")
@RequiredArgsConstructor
@Slf4j
public class AdminMatchController {

    private final MatchService matchService;
    private final BracketService bracketService;
    private final TournamentService tournamentService;
    private final TournamentAccessService tournamentAccessService;

    @GetMapping
    public String viewMatches(@PathVariable Long tournamentId,
                              Model model,
                              @AuthenticationPrincipal Owner owner) {
        log.info("Viendo partidos del torneo: {}", tournamentId);
        tournamentAccessService.assertCanManageTournament(owner, tournamentId);

        TournamentDto tournament = tournamentService.getTournamentDtoById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Torneo no encontrado con ID: " + tournamentId));

        List<List<BracketMatchDto>> bracket = bracketService.getTournamentBracket(tournamentId);

        model.addAttribute("tournament", tournament);
        model.addAttribute("bracket", bracket);

        if (bracket.isEmpty()) {
            List<com.padle.core.padelcoreservice.dto.TournamentRegistrationDto> confirmedRegistrations =
                    tournamentService.getRegistrationsByTournament(tournamentId).stream()
                            .filter(r -> r.getStatus() == RegistrationStatus.CONFIRMED)
                            .toList();
            model.addAttribute("confirmedRegistrations", confirmedRegistrations);
        }

        return "admin/tournaments/matches";
    }

    @PostMapping("/{matchId}")
    public String updateMatchResult(@PathVariable Long tournamentId,
                                    @PathVariable Long matchId,
                                    @ModelAttribute MatchDto matchDto,
                                    @AuthenticationPrincipal Owner owner,
                                    RedirectAttributes redirectAttributes) {
        log.info("Actualizando resultado del partido: {}", matchId);
        tournamentAccessService.assertCanManageTournament(owner, tournamentId);

        try {
            matchService.updateMatchResult(matchId, matchDto);

            // Avanzar ganador si es necesario
            if (matchDto.getGanadorId() != null) {
                bracketService.advanceWinner(matchId, matchDto.getGanadorId());
            }

            redirectAttributes.addFlashAttribute("successMessage",
                    "Resultado del partido actualizado correctamente");
        } catch (Exception e) {
            log.error("Error actualizando partido: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error al actualizar el partido: " + e.getMessage());
        }

        return "redirect:/admin/tournaments/" + tournamentId + "/matches";
    }

    @PostMapping("/generate")
    public String generateBracket(@PathVariable Long tournamentId,
                                  @RequestParam List<Long> playerIds,
                                  @AuthenticationPrincipal Owner owner,
                                  RedirectAttributes redirectAttributes) {
        log.info("Generando bracket para torneo: {}", tournamentId);
        tournamentAccessService.assertCanManageTournament(owner, tournamentId);

        try {
            bracketService.generateInitialBracket(tournamentId, playerIds);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Bracket generado correctamente");
        } catch (Exception e) {
            log.error("Error generando bracket: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Error al generar bracket: " + e.getMessage());
        }

        return "redirect:/admin/tournaments/" + tournamentId + "/matches";
    }
}