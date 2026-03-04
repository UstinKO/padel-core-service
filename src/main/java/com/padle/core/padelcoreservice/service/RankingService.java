package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.RankingDto;
import com.padle.core.padelcoreservice.mapper.RankingMapper;
import com.padle.core.padelcoreservice.model.Match;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.Ranking;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import com.padle.core.padelcoreservice.repository.RankingRepository;
import com.padle.core.padelcoreservice.repository.TournamentRegistrationRepository;
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
public class RankingService {

    private final RankingRepository rankingRepository;
    private final TournamentRegistrationRepository registrationRepository;
    private final PlayerService playerService;
    private final RankingMapper rankingMapper;

    public List<RankingDto> getRankingCompleto() {
        log.debug("Obteniendo ranking completo de jugadores");
        List<Ranking> rankings = rankingRepository.findAllOrderByPuntosDesc();

        // Actualizar posiciones
        actualizarPosiciones(rankings);

        return rankings.stream()
                .map(this::mapToDtoWithPlayerInfo)
                .collect(Collectors.toList());
    }

    public List<RankingDto> getTopRanking(int limit) {
        log.debug("Obteniendo top {} del ranking", limit);
        List<Ranking> rankings = rankingRepository.findTopRanking(limit);
        return rankings.stream()
                .map(this::mapToDtoWithPlayerInfo)
                .collect(Collectors.toList());
    }

    @Transactional
    public RankingDto inicializarRanking(Long playerId) {
        log.info("Inicializando ranking para jugador: {}", playerId);

        // Verificar si ya existe
        Optional<Ranking> existing = rankingRepository.findByPlayerId(playerId);
        if (existing.isPresent()) {
            return mapToDtoWithPlayerInfo(existing.get());
        }

        // Получаем игрока для связи
        PlayerPadel player = playerService.getPlayerById(playerId)
                .orElseThrow(() -> new RuntimeException("Player not found with id: " + playerId));

        // Crear nuevo ranking
        Ranking ranking = Ranking.builder()
                .player(player)
                .puntos(1000) // Puntos iniciales
                .torneosJugados(0)
                .torneosGanados(0)
                .partidosGanados(0)
                .partidosPerdidos(0)
                .setsGanados(0)
                .setsPerdidos(0)
                .nivelActual("C9") // Nivel inicial
                .build();

        Ranking saved = rankingRepository.save(ranking);
        log.info("Ranking inicializado para jugador: {}", playerId);

        return mapToDtoWithPlayerInfo(saved);
    }

    @Transactional
    public void actualizarRankingJugador(Long playerId) {
        Optional<Ranking> rankingOpt = rankingRepository.findByPlayerId(playerId);
        if (rankingOpt.isEmpty()) {
            inicializarRanking(playerId);
            return;
        }

        Ranking ranking = rankingOpt.get();

        // Получаем статистику игрока
        long torneosJugados = registrationRepository.countByPlayerIdAndStatus(
                playerId, RegistrationStatus.PARTICIPATED);

        long torneosGanados = calcularTorneosGanados(playerId);

        ranking.setTorneosJugados((int) torneosJugados);
        ranking.setTorneosGanados((int) torneosGanados);

        // Calcular puntos: 1000 base + 50 por torneo jugado + 200 por torneo ganado
        int puntos = 1000 + (int) torneosJugados * 50 + (int) torneosGanados * 200;
        ranking.setPuntos(puntos);

        // Actualizar nivel basado en puntos
        actualizarNivel(ranking);

        rankingRepository.save(ranking);
        log.info("Ranking actualizado para jugador: {}", playerId);
    }

    private void actualizarNivel(Ranking ranking) {
        int puntos = ranking.getPuntos();
        if (puntos >= 2500) {
            ranking.setNivelActual("C5");
        } else if (puntos >= 2000) {
            ranking.setNivelActual("C6");
        } else if (puntos >= 1600) {
            ranking.setNivelActual("C7");
        } else if (puntos >= 1300) {
            ranking.setNivelActual("C8");
        } else {
            ranking.setNivelActual("C9");
        }
    }

    private long calcularTorneosGanados(Long playerId) {
        // Por ahora simulamos que no hay torneos ganados
        // En el futuro, aquí iría la lógica real
        return 0;
    }

    private void actualizarPosiciones(List<Ranking> rankings) {
        for (int i = 0; i < rankings.size(); i++) {
            Ranking ranking = rankings.get(i);
            int nuevaPosicion = i + 1;

            if (ranking.getPosicionActual() == null) {
                ranking.setPosicionAnterior(nuevaPosicion);
                ranking.setPosicionActual(nuevaPosicion);
            } else {
                ranking.setPosicionAnterior(ranking.getPosicionActual());
                ranking.setPosicionActual(nuevaPosicion);
            }
        }
        rankingRepository.saveAll(rankings);
    }

    private RankingDto mapToDtoWithPlayerInfo(Ranking ranking) {
        RankingDto dto = rankingMapper.toDto(ranking);

        // Получаем информацию об игроке
        PlayerPadel player = ranking.getPlayer();
        if (player != null) {
            dto.setPlayerNombre(player.getNombre());
            dto.setPlayerApellido(player.getApellido());
            dto.setPlayerNombreCompleto(player.getNombreCompleto());
            dto.setPlayerEmail(player.getEmail());
        }

        dto.setWinRate(ranking.getWinRate());
        dto.setTendencia(ranking.getTendencia());

        return dto;
    }

    @Transactional
    public void registrarVictoria(Long playerId, Match match) {
        Ranking ranking = rankingRepository.findByPlayerId(playerId)
                .orElseGet(() -> inicializarRankingEntity(playerId));

        ranking.setPartidosGanados(ranking.getPartidosGanados() + 1);
        ranking.setTorneosJugados(ranking.getTorneosJugados() + 1);

        // Calcular puntos según la importancia del partido
        int puntosPartido = calcularPuntosPartido(match);
        ranking.setPuntos(ranking.getPuntos() + puntosPartido);

        // Actualizar sets
        actualizarSets(ranking, match, true);

        actualizarNivel(ranking);
        rankingRepository.save(ranking);
    }

    @Transactional
    public void registrarDerrota(Long playerId, Match match) {
        Ranking ranking = rankingRepository.findByPlayerId(playerId)
                .orElseGet(() -> inicializarRankingEntity(playerId));

        ranking.setPartidosPerdidos(ranking.getPartidosPerdidos() + 1);
        ranking.setTorneosJugados(ranking.getTorneosJugados() + 1);

        // Puntos por participación aunque pierda
        ranking.setPuntos(ranking.getPuntos() + 25);

        // Actualizar sets
        actualizarSets(ranking, match, false);

        actualizarNivel(ranking);
        rankingRepository.save(ranking);
    }

    private int calcularPuntosPartido(Match match) {
        // Puntos base según la ronda
        int puntosBase = switch (match.getRonda()) {
            case 1 -> 50;  // Primera ronda
            case 2 -> 75;  // Cuartos
            case 3 -> 100; // Semifinal
            case 4 -> 150; // Final
            default -> 40;
        };

        // Bonus por sets ganados
        int setsGanados = 0;
        if (match.getSet1Equipo1() != null && match.getSet1Equipo2() != null) setsGanados++;
        if (match.getSet2Equipo1() != null && match.getSet2Equipo2() != null) setsGanados++;
        if (match.getSet3Equipo1() != null && match.getSet3Equipo2() != null) setsGanados++;

        return puntosBase + (setsGanados * 10);
    }

    private void actualizarSets(Ranking ranking, Match match, boolean esGanador) {
        if (esGanador) {
            ranking.setSetsGanados(ranking.getSetsGanados() +
                    (match.getSet1Equipo1() != null && match.getSet1Equipo1() > match.getSet1Equipo2() ? 1 : 0) +
                    (match.getSet2Equipo1() != null && match.getSet2Equipo1() > match.getSet2Equipo2() ? 1 : 0) +
                    (match.getSet3Equipo1() != null && match.getSet3Equipo1() > match.getSet3Equipo2() ? 1 : 0));

            ranking.setSetsPerdidos(ranking.getSetsPerdidos() +
                    (match.getSet1Equipo1() != null && match.getSet1Equipo1() < match.getSet1Equipo2() ? 1 : 0) +
                    (match.getSet2Equipo1() != null && match.getSet2Equipo1() < match.getSet2Equipo2() ? 1 : 0) +
                    (match.getSet3Equipo1() != null && match.getSet3Equipo1() < match.getSet3Equipo2() ? 1 : 0));
        } else {
            ranking.setSetsGanados(ranking.getSetsGanados() +
                    (match.getSet1Equipo2() != null && match.getSet1Equipo2() > match.getSet1Equipo1() ? 1 : 0) +
                    (match.getSet2Equipo2() != null && match.getSet2Equipo2() > match.getSet2Equipo1() ? 1 : 0) +
                    (match.getSet3Equipo2() != null && match.getSet3Equipo2() > match.getSet3Equipo1() ? 1 : 0));

            ranking.setSetsPerdidos(ranking.getSetsPerdidos() +
                    (match.getSet1Equipo2() != null && match.getSet1Equipo2() < match.getSet1Equipo1() ? 1 : 0) +
                    (match.getSet2Equipo2() != null && match.getSet2Equipo2() < match.getSet2Equipo1() ? 1 : 0) +
                    (match.getSet3Equipo2() != null && match.getSet3Equipo2() < match.getSet3Equipo1() ? 1 : 0));
        }
    }

    private Ranking inicializarRankingEntity(Long playerId) {
        PlayerPadel player = playerService.getPlayerById(playerId)
                .orElseThrow(() -> new RuntimeException("Player not found with id: " + playerId));

        Ranking ranking = Ranking.builder()
                .player(player)
                .puntos(1000)
                .torneosJugados(0)
                .torneosGanados(0)
                .partidosGanados(0)
                .partidosPerdidos(0)
                .setsGanados(0)
                .setsPerdidos(0)
                .nivelActual("C9")
                .build();
        return rankingRepository.save(ranking);
    }
}