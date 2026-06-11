package com.padle.core.padelcoreservice.controller.view;

import com.padle.core.padelcoreservice.dto.KingOfCourtStateDTO;
import com.padle.core.padelcoreservice.model.TournamentKingOfCourt;
import com.padle.core.padelcoreservice.repository.TournamentKingOfCourtRepository;
import com.padle.core.padelcoreservice.service.KingOfCourtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class KingOfCourtViewController {

    private final TournamentKingOfCourtRepository kingRepository;
    private final KingOfCourtService kingOfCourtService;

    // URL вида /tournaments/king-of-court/{kingId} — по ID записи TournamentKingOfCourt
    @GetMapping("/tournaments/king-of-court/{kingId}")
    public String viewKingOfCourtByKingId(@PathVariable Long kingId, Model model) {
        TournamentKingOfCourt king = kingRepository.findById(kingId).orElse(null);
        if (king == null) {
            return "redirect:/?error=not-found";
        }

        KingOfCourtStateDTO viewData = kingOfCourtService.getCurrentState(king.getId());

        model.addAttribute("kingData", viewData);
        model.addAttribute("tournamentId", king.getTournament().getId());
        model.addAttribute("tournamentName", king.getTournament().getNombre());
        model.addAttribute("isViewer", true);

        return "king-of-court-view";
    }

    // URL вида /torneo/{tournamentId}/king-of-court — по ID родительского турнира
    @GetMapping("/torneo/{tournamentId}/king-of-court")
    public String viewKingOfCourt(@PathVariable Long tournamentId, Model model) {

        // Сначала ищем активный турнир
        List<TournamentKingOfCourt> kings =
                kingRepository.findAllByTournamentIdAndIsActiveTrue(tournamentId);

        // Если активного нет — ищем завершённый (isActive=false, isFinished=true)
        if (kings.isEmpty()) {
            kings = kingRepository.findAllByTournamentId(tournamentId)
                    .stream()
                    .filter(k -> Boolean.TRUE.equals(k.getIsFinished()))
                    .toList();
        }

        if (kings.isEmpty()) {
            return "redirect:/torneo/" + tournamentId + "?error=no-active-tournament";
        }

        TournamentKingOfCourt king = kings.get(0);
        KingOfCourtStateDTO viewData = kingOfCourtService.getCurrentState(king.getId());

        model.addAttribute("kingData", viewData);
        model.addAttribute("tournamentId", tournamentId);
        model.addAttribute("tournamentName", king.getTournament().getNombre());
        model.addAttribute("isViewer", true);

        return "king-of-court-view";
    }
}