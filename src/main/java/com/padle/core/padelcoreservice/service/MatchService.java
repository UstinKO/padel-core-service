package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.MatchDto;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.mapper.MatchMapper;
import com.padle.core.padelcoreservice.model.Match;
import com.padle.core.padelcoreservice.model.enums.MatchStatus;
import com.padle.core.padelcoreservice.repository.MatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class MatchService {

    private final MatchRepository matchRepository;
    private final MatchMapper matchMapper;
    private final PlayerService playerService;
    private final TournamentService tournamentService;
    private final RankingService rankingService;

    @Transactional
    public MatchDto updateMatchResult(Long matchId, MatchDto resultDto) {
        Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Partido no encontrado con ID: " + matchId));

        // Actualizar resultados
        match.setSet1Equipo1(resultDto.getSet1Equipo1());
        match.setSet1Equipo2(resultDto.getSet1Equipo2());
        match.setSet2Equipo1(resultDto.getSet2Equipo1());
        match.setSet2Equipo2(resultDto.getSet2Equipo2());
        match.setSet3Equipo1(resultDto.getSet3Equipo1());
        match.setSet3Equipo2(resultDto.getSet3Equipo2());
        match.setSuperSetEquipo1(resultDto.getSuperSetEquipo1());
        match.setSuperSetEquipo2(resultDto.getSuperSetEquipo2());

        // Determinar ganador
        determinarGanador(match);

        // Calcular puntos totales
        match.setPuntosEquipo1(calcularPuntosEquipo(match, true));
        match.setPuntosEquipo2(calcularPuntosEquipo(match, false));

        match.setEstado(MatchStatus.FINALIZADO);
        match.setFechaFin(java.time.LocalDateTime.now());

        Match updatedMatch = matchRepository.save(match);
        log.info("Resultado actualizado para partido ID: {}", matchId);

        // Actualizar rankings después del partido
        actualizarRankingsPostPartido(updatedMatch);

        return mapToDtoWithDetails(updatedMatch);
    }

    private void determinarGanador(Match match) {
        int setsGanadosEquipo1 = 0;
        int setsGanadosEquipo2 = 0;

        if (match.getSet1Equipo1() != null && match.getSet1Equipo2() != null) {
            if (match.getSet1Equipo1() > match.getSet1Equipo2()) setsGanadosEquipo1++;
            else setsGanadosEquipo2++;
        }
        if (match.getSet2Equipo1() != null && match.getSet2Equipo2() != null) {
            if (match.getSet2Equipo1() > match.getSet2Equipo2()) setsGanadosEquipo1++;
            else setsGanadosEquipo2++;
        }
        if (match.getSet3Equipo1() != null && match.getSet3Equipo2() != null) {
            if (match.getSet3Equipo1() > match.getSet3Equipo2()) setsGanadosEquipo1++;
            else setsGanadosEquipo2++;
        }
        if (match.getSuperSetEquipo1() != null && match.getSuperSetEquipo2() != null) {
            if (match.getSuperSetEquipo1() > match.getSuperSetEquipo2()) setsGanadosEquipo1++;
            else setsGanadosEquipo2++;
        }

        if (setsGanadosEquipo1 > setsGanadosEquipo2) {
            match.setGanadorId(match.getPlayer1Id());
            match.setPerdedorId(match.getPlayer2Id());
        } else if (setsGanadosEquipo2 > setsGanadosEquipo1) {
            match.setGanadorId(match.getPlayer2Id());
            match.setPerdedorId(match.getPlayer1Id());
        }
        // Si hay empate, se mantiene null y habrá que resolver
    }

    private Integer calcularPuntosEquipo(Match match, boolean equipo1) {
        int puntos = 0;
        if (equipo1) {
            puntos += match.getSet1Equipo1() != null ? match.getSet1Equipo1() : 0;
            puntos += match.getSet2Equipo1() != null ? match.getSet2Equipo1() : 0;
            puntos += match.getSet3Equipo1() != null ? match.getSet3Equipo1() : 0;
            puntos += match.getSuperSetEquipo1() != null ? match.getSuperSetEquipo1() : 0;
        } else {
            puntos += match.getSet1Equipo2() != null ? match.getSet1Equipo2() : 0;
            puntos += match.getSet2Equipo2() != null ? match.getSet2Equipo2() : 0;
            puntos += match.getSet3Equipo2() != null ? match.getSet3Equipo2() : 0;
            puntos += match.getSuperSetEquipo2() != null ? match.getSuperSetEquipo2() : 0;
        }
        return puntos;
    }

    private void actualizarRankingsPostPartido(Match match) {
        if (match.getGanadorId() == null) return;

        // Actualizar estadísticas para el ganador
        actualizarEstadisticasJugador(match.getGanadorId(), true, match);

        // Actualizar estadísticas para el perdedor
        if (match.getPerdedorId() != null) {
            actualizarEstadisticasJugador(match.getPerdedorId(), false, match);
        }

        // Si es partido de parejas, actualizar también al compañero
        if (match.isPartidoParejas()) {
            // Lógica para parejas
        }
    }

    private void actualizarEstadisticasJugador(Long playerId, boolean esGanador, Match match) {
        // Obtener o crear ranking
        rankingService.inicializarRanking(playerId);

        // Actualizar estadísticas en RankingService
        if (esGanador) {
            rankingService.registrarVictoria(playerId, match);
        } else {
            rankingService.registrarDerrota(playerId, match);
        }
    }

    private MatchDto mapToDtoWithDetails(Match match) {
        MatchDto dto = matchMapper.toDto(match);

        // Obtener información del torneo
        tournamentService.getTournamentById(match.getTournamentId())
                .ifPresent(t -> dto.setTournamentNombre(t.getNombre()));

        // Obtener información de los jugadores
        if (match.getPlayer1Id() != null) {
            playerService.getPlayerById(match.getPlayer1Id())
                    .ifPresent(p -> {
                        dto.setPlayer1Nombre(p.getNombre());
                        dto.setPlayer1Apellido(p.getApellido());
                        dto.setPlayer1NombreCompleto(p.getNombreCompleto());
                    });
        }
        if (match.getPlayer2Id() != null) {
            playerService.getPlayerById(match.getPlayer2Id())
                    .ifPresent(p -> {
                        dto.setPlayer2Nombre(p.getNombre());
                        dto.setPlayer2Apellido(p.getApellido());
                        dto.setPlayer2NombreCompleto(p.getNombreCompleto());
                    });
        }

        // Nombre del ganador
        if (match.getGanadorId() != null) {
            playerService.getPlayerById(match.getGanadorId())
                    .ifPresent(p -> dto.setGanadorNombre(p.getNombreCompleto()));
        }

        return dto;
    }

    public List<MatchDto> getMatchesByStatus(MatchStatus status) {
        log.debug("Obteniendo partidos con estado: {}", status);
        return matchRepository.findByEstado(status).stream()
                .map(this::mapToDtoWithDetails)
                .collect(Collectors.toList());
    }

    public List<MatchDto> getUpcomingMatches(int limit) {
        log.debug("Obteniendo {} próximos partidos", limit);
        return matchRepository.findUpcomingMatches(limit).stream()
                .map(this::mapToDtoWithDetails)
                .collect(Collectors.toList());
    }
}