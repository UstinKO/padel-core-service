package com.padle.core.padelcoreservice.repository.americano;

import com.padle.core.padelcoreservice.model.americano.AmericanoTeam;
import com.padle.core.padelcoreservice.model.enums.AmericanoPlayerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AmericanoTeamRepository extends JpaRepository<AmericanoTeam, Long> {

    List<AmericanoTeam> findByTournamentId(Long tournamentId);

    List<AmericanoTeam> findByTournamentIdAndStatus(Long tournamentId, AmericanoPlayerStatus status);

    Optional<AmericanoTeam> findByTournamentIdAndPlayer1Id(Long tournamentId, Long player1Id);

    Optional<AmericanoTeam> findByTournamentIdAndPlayer2Id(Long tournamentId, Long player2Id);

    @Query("SELECT t FROM AmericanoTeam t WHERE t.tournament.id = :tournamentId " +
            "ORDER BY t.totalScore DESC, t.matchesWon DESC, " +
            "(t.pointsScored - t.pointsConceded) DESC, t.matchesLost ASC")
    List<AmericanoTeam> findRankingByTournamentId(@Param("tournamentId") Long tournamentId);

    @Query("SELECT COUNT(t) FROM AmericanoTeam t WHERE t.tournament.id = :tournamentId " +
            "AND t.status = 'ACTIVE'")
    long countActiveTeams(@Param("tournamentId") Long tournamentId);

    boolean existsByTournamentIdAndPlayer1Id(Long tournamentId, Long player1Id);
}