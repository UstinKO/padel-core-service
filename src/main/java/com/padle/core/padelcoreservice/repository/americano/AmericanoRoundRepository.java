package com.padle.core.padelcoreservice.repository.americano;

import com.padle.core.padelcoreservice.model.americano.AmericanoRound;
import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AmericanoRoundRepository extends JpaRepository<AmericanoRound, Long> {

    List<AmericanoRound> findByTournamentIdOrderByRoundNumberAsc(Long tournamentId);

    Optional<AmericanoRound> findByTournamentIdAndRoundNumber(Long tournamentId, Integer roundNumber);

    @Query("SELECT ar FROM AmericanoRound ar WHERE ar.tournament.id = :tournamentId " +
            "AND ar.status = :status ORDER BY ar.roundNumber")
    List<AmericanoRound> findByTournamentIdAndStatus(
            @Param("tournamentId") Long tournamentId,
            @Param("status") AmericanoRoundStatus status);

    @Query("SELECT MAX(ar.roundNumber) FROM AmericanoRound ar WHERE ar.tournament.id = :tournamentId")
    Optional<Integer> findMaxRoundNumber(@Param("tournamentId") Long tournamentId);

    @Query("SELECT COUNT(ar) FROM AmericanoRound ar WHERE ar.tournament.id = :tournamentId " +
            "AND ar.status = 'COMPLETED'")
    int countCompletedRounds(@Param("tournamentId") Long tournamentId);

    @Query("SELECT ar FROM AmericanoRound ar WHERE ar.tournament.id = :tournamentId " +
            "AND ar.phase = :phase ORDER BY ar.roundNumber")
    List<AmericanoRound> findByTournamentIdAndPhase(
            @Param("tournamentId") Long tournamentId,
            @Param("phase") com.padle.core.padelcoreservice.model.enums.TournamentPhase phase);
}