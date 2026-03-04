package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.Match;
import com.padle.core.padelcoreservice.model.enums.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<Match, Long> {

    List<Match> findByTournamentId(Long tournamentId);

    @Query("SELECT m FROM Match m WHERE m.player1Id = :playerId OR m.player2Id = :playerId OR m.player3Id = :playerId OR m.player4Id = :playerId")
    List<Match> findAllMatchesByPlayerId(@Param("playerId") Long playerId);

    Optional<Match> findByTournamentIdAndRondaAndPartidoNumero(Long tournamentId, Integer ronda, Integer partidoNumero);

    List<Match> findByEstado(MatchStatus estado);

    @Query("SELECT m FROM Match m WHERE m.estado = 'PROGRAMADO' AND m.fechaProgramada >= CURRENT_TIMESTAMP ORDER BY m.fechaProgramada ASC LIMIT :limit")
    List<Match> findUpcomingMatches(@Param("limit") int limit);
}