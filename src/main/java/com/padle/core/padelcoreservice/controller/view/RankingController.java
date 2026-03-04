package com.padle.core.padelcoreservice.controller.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.padle.core.padelcoreservice.dto.RankingDto;
import com.padle.core.padelcoreservice.service.RankingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/ranking")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public String verRanking(Model model) {
        log.info("Accediendo a página de ranking");

        try {
            // Obtener ranking completo
            List<RankingDto> rankingCompleto = rankingService.getRankingCompleto();

            // Obtener top 10
            List<RankingDto> topRanking = rankingService.getTopRanking(10);

            // Convertir a JSON para JavaScript
            String rankingJson = objectMapper.writeValueAsString(rankingCompleto);
            String topRankingJson = objectMapper.writeValueAsString(topRanking);

            model.addAttribute("ranking", rankingCompleto);
            model.addAttribute("topRanking", topRanking);
            model.addAttribute("rankingJson", rankingJson);
            model.addAttribute("topRankingJson", topRankingJson);
            model.addAttribute("totalJugadores", rankingCompleto.size());

        } catch (Exception e) {
            log.error("Error al cargar ranking: {}", e.getMessage());
        }

        return "ranking";
    }
}