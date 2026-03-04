package com.padle.core.padelcoreservice.controller.player;

import com.padle.core.padelcoreservice.dto.BracketMatchDto;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.service.BracketService;
import com.padle.core.padelcoreservice.service.TournamentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/players/tournaments")
@RequiredArgsConstructor
@Slf4j
public class PlayerMatchController {

    private final BracketService bracketService;
    private final TournamentService tournamentService;
    private final ObjectMapper objectMapper;

    @GetMapping("/{tournamentId}/bracket")
    public String viewTournamentBracket(@PathVariable Long tournamentId,
                                        @AuthenticationPrincipal PlayerPadel player,
                                        Model model) {
        log.info("Jugador {} viendo bracket del torneo: {}", player.getId(), tournamentId);

        TournamentDto tournament = tournamentService.getTournamentById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Torneo no encontrado"));

        List<List<BracketMatchDto>> bracket = bracketService.getTournamentBracket(tournamentId);

        // Marcar los partidos donde participa el jugador
        bracket.forEach(round -> round.forEach(match -> {
            boolean participa = (match.getPlayer1Id() != null && match.getPlayer1Id().equals(player.getId())) ||
                    (match.getPlayer2Id() != null && match.getPlayer2Id().equals(player.getId()));
            match.setIsJugadorParticipa(participa);
        }));

        // Encontrar próximo partido del jugador
        Optional<BracketMatchDto> nextMatch = bracketService.getNextMatchForPlayer(player.getId());

        try {
            String bracketJson = objectMapper.writeValueAsString(bracket);
            model.addAttribute("bracketJson", bracketJson);
        } catch (Exception e) {
            log.error("Error convirtiendo bracket a JSON", e);
        }

        model.addAttribute("tournament", tournament);
        model.addAttribute("bracket", bracket);
        model.addAttribute("nextMatch", nextMatch.orElse(null));
        model.addAttribute("player", player);

        return "players/tournament-bracket";
    }
}