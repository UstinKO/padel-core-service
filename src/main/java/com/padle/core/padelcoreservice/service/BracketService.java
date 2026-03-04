package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.BracketMatchDto;
import com.padle.core.padelcoreservice.dto.MatchDto;
import com.padle.core.padelcoreservice.model.Match;
import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.MatchStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import com.padle.core.padelcoreservice.repository.MatchRepository;
import com.padle.core.padelcoreservice.repository.TournamentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BracketService {

    private final MatchRepository matchRepository;
    private final TournamentRepository tournamentRepository;
    private final PlayerService playerService;

    /**
     * Obtiene la bracket completa de un torneo
     */
    public List<List<BracketMatchDto>> getTournamentBracket(Long tournamentId) {
        log.info("Generando bracket para torneo: {}", tournamentId);

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Torneo no encontrado"));

        List<Match> matches = matchRepository.findByTournamentId(tournamentId);

        // Organizar por rondas
        Map<Integer, List<Match>> matchesByRound = matches.stream()
                .collect(Collectors.groupingBy(Match::getRonda));

        // Determinar número de rondas
        int maxRound = matchesByRound.keySet().stream()
                .max(Integer::compareTo)
                .orElse(0);

        List<List<BracketMatchDto>> bracket = new ArrayList<>();

        for (int ronda = 1; ronda <= maxRound; ronda++) {
            List<Match> roundMatches = matchesByRound.getOrDefault(ronda, new ArrayList<>());

            // Ordenar por número de partido
            roundMatches.sort(Comparator.comparing(Match::getPartidoNumero));

            List<BracketMatchDto> roundDto = roundMatches.stream()
                    .map(this::convertToBracketDto)
                    .collect(Collectors.toList());

            bracket.add(roundDto);
        }

        return bracket;
    }

    /**
     * Obtiene el próximo partido de un jugador
     */
    public Optional<BracketMatchDto> getNextMatchForPlayer(Long playerId) {
        // Buscar partidos programados donde el jugador participa
        List<Match> upcomingMatches = matchRepository.findAllMatchesByPlayerId(playerId).stream()
                .filter(m -> m.getEstado() == MatchStatus.PROGRAMADO)
                .filter(m -> m.getFechaProgramada() != null)
                .filter(m -> m.getFechaProgramada().isAfter(java.time.LocalDateTime.now()))
                .sorted(Comparator.comparing(Match::getFechaProgramada))
                .collect(Collectors.toList());

        if (upcomingMatches.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(convertToBracketDto(upcomingMatches.get(0)));
    }

    /**
     * Genera la bracket inicial para un torneo
     */
    @Transactional
    public void generateInitialBracket(Long tournamentId, List<Long> playerIds) {
        log.info("Generando bracket inicial para torneo: {}", tournamentId);

        Tournament tournament = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Torneo no encontrado"));

        int numPlayers = playerIds.size();
        int numRondas = (int) Math.ceil(Math.log(numPlayers) / Math.log(2));
        int totalMatches = numPlayers - 1;

        // Determinar número de partidos en primera ronda
        int matchesInFirstRound = numPlayers / 2;

        // Crear partidos de primera ronda
        for (int i = 0; i < matchesInFirstRound; i++) {
            Long player1Id = playerIds.get(i * 2);
            Long player2Id = playerIds.get(i * 2 + 1);

            Match match = Match.builder()
                    .tournamentId(tournamentId)
                    .ronda(1)
                    .partidoNumero(i + 1)
                    .tipo(tournament.getTipo() == TournamentType.KING_OF_COURT ?
                            com.padle.core.padelcoreservice.model.enums.MatchType.INDIVIDUAL :
                            com.padle.core.padelcoreservice.model.enums.MatchType.PAREJAS)
                    .player1Id(player1Id)
                    .player2Id(player2Id)
                    .estado(MatchStatus.PROGRAMADO)
                    .build();

            matchRepository.save(match);
        }

        // Crear partidos de rondas siguientes (vacíos, se llenarán con ganadores)
        int partidosPorRonda = matchesInFirstRound;
        for (int ronda = 2; ronda <= numRondas; ronda++) {
            partidosPorRonda = partidosPorRonda / 2;
            for (int i = 0; i < partidosPorRonda; i++) {
                Match match = Match.builder()
                        .tournamentId(tournamentId)
                        .ronda(ronda)
                        .partidoNumero(i + 1)
                        .tipo(tournament.getTipo() == TournamentType.KING_OF_COURT ?
                                com.padle.core.padelcoreservice.model.enums.MatchType.INDIVIDUAL :
                                com.padle.core.padelcoreservice.model.enums.MatchType.PAREJAS)
                        .estado(MatchStatus.PROGRAMADO)
                        .build();

                matchRepository.save(match);
            }
        }

        log.info("Bracket inicial generado con {} partidos", totalMatches);
    }

    /**
     * Avanza al ganador al siguiente partido
     */
    @Transactional
    public void advanceWinner(Long matchId, Long winnerId) {
        Match currentMatch = matchRepository.findById(matchId)
                .orElseThrow(() -> new RuntimeException("Partido no encontrado"));

        // Buscar el siguiente partido
        Optional<Match> nextMatch = findNextMatch(currentMatch);

        if (nextMatch.isPresent()) {
            Match next = nextMatch.get();

            // Asignar el ganador al siguiente partido
            if (next.getPlayer1Id() == null) {
                next.setPlayer1Id(winnerId);
            } else if (next.getPlayer2Id() == null) {
                next.setPlayer2Id(winnerId);
            }

            matchRepository.save(next);
            log.info("Ganador {} avanzado al siguiente partido", winnerId);
        }
    }

    private Optional<Match> findNextMatch(Match currentMatch) {
        int siguienteRonda = currentMatch.getRonda() + 1;
        int partidoDestino = (int) Math.ceil(currentMatch.getPartidoNumero() / 2.0);

        return matchRepository.findByTournamentIdAndRondaAndPartidoNumero(
                currentMatch.getTournamentId(), siguienteRonda, partidoDestino);
    }

    private BracketMatchDto convertToBracketDto(Match match) {
        BracketMatchDto dto = new BracketMatchDto();
        dto.setId(match.getId());
        dto.setTournamentId(match.getTournamentId());
        dto.setRonda(match.getRonda());
        dto.setPartidoNumero(match.getPartidoNumero());
        dto.setTipo(match.getTipo().toString());

        // Jugadores
        if (match.getPlayer1Id() != null) {
            dto.setPlayer1Id(match.getPlayer1Id());
            playerService.getPlayerById(match.getPlayer1Id())
                    .ifPresent(p -> {
                        dto.setPlayer1Nombre(p.getNombre());
                        dto.setPlayer1Apellido(p.getApellido());
                        dto.setPlayer1NombreCompleto(p.getNombreCompleto());
                        dto.setPlayer1Email(p.getEmail());
                    });
        }

        if (match.getPlayer2Id() != null) {
            dto.setPlayer2Id(match.getPlayer2Id());
            playerService.getPlayerById(match.getPlayer2Id())
                    .ifPresent(p -> {
                        dto.setPlayer2Nombre(p.getNombre());
                        dto.setPlayer2Apellido(p.getApellido());
                        dto.setPlayer2NombreCompleto(p.getNombreCompleto());
                        dto.setPlayer2Email(p.getEmail());
                    });
        }

        // Resultados
        dto.setSet1Equipo1(match.getSet1Equipo1());
        dto.setSet1Equipo2(match.getSet1Equipo2());
        dto.setSet2Equipo1(match.getSet2Equipo1());
        dto.setSet2Equipo2(match.getSet2Equipo2());
        dto.setSet3Equipo1(match.getSet3Equipo1());
        dto.setSet3Equipo2(match.getSet3Equipo2());
        dto.setSuperSetEquipo1(match.getSuperSetEquipo1());
        dto.setSuperSetEquipo2(match.getSuperSetEquipo2());

        dto.setGanadorId(match.getGanadorId());
        if (match.getGanadorId() != null) {
            playerService.getPlayerById(match.getGanadorId())
                    .ifPresent(p -> dto.setGanadorNombre(p.getNombreCompleto()));
        }

        dto.setEstado(match.getEstado().toString());
        dto.setFechaProgramada(match.getFechaProgramada());
        dto.setFechaInicio(match.getFechaInicio());
        dto.setFechaFin(match.getFechaFin());
        dto.setCancha(match.getCancha());

        dto.setIsFinalizado(match.isFinalizado());
        dto.setIsEnCurso(match.isEnCurso());
        dto.setIsProgramado(match.getEstado() == MatchStatus.PROGRAMADO);
        dto.setResultadoString(match.getResultadoString());

        // Encontrar siguiente partido
        findNextMatch(match).ifPresent(next -> {
            dto.setSiguientePartidoId(next.getId());
            dto.setSiguientePartidoRonda(next.getRonda());
            dto.setSiguientePartidoNumero(next.getPartidoNumero());
        });

        return dto;
    }
}