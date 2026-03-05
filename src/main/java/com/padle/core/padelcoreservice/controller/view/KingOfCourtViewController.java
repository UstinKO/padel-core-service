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

    @GetMapping("/torneo/{tournamentId}/king-of-court")
    public String viewKingOfCourt(@PathVariable Long tournamentId, Model model) {

        List<TournamentKingOfCourt> activeKings = kingRepository
                .findAllByTournamentIdAndIsActiveTrue(tournamentId);

        if (activeKings.isEmpty()) {
            return "redirect:/torneo/" + tournamentId + "?error=no-active-tournament";
        }

        TournamentKingOfCourt king = activeKings.get(0);

        // Получаем данные для отображения (без кнопок управления)
        KingOfCourtStateDTO viewData = kingOfCourtService.getCurrentState(king.getId());

        model.addAttribute("kingData", viewData);
        model.addAttribute("tournamentId", tournamentId);
        model.addAttribute("tournamentName", king.getTournament().getNombre());
        model.addAttribute("isViewer", true); // для разграничения прав

        return "king-of-court-view";
    }
}