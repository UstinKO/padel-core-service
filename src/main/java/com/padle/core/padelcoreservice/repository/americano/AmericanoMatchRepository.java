package com.padle.core.padelcoreservice.repository.americano;

import com.padle.core.padelcoreservice.model.americano.AmericanoMatch;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.enums.AmericanoRoundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AmericanoMatchRepository extends JpaRepository<AmericanoMatch, Long> {

    List<AmericanoMatch> findByRoundId(Long roundId);

    List<AmericanoMatch> findByTournamentIdOrderByRoundIdAscMatchNumberAsc(Long tournamentId);

    @Query("SELECT am FROM AmericanoMatch am WHERE am.tournament.id = :tournamentId " +
            "AND am.status = :status ORDER BY am.round.roundNumber, am.matchNumber")
    List<AmericanoMatch> findByTournamentIdAndStatus(
            @Param("tournamentId") Long tournamentId,
            @Param("status") AmericanoRoundStatus status);

    @Query("SELECT am FROM AmericanoMatch am WHERE am.round.id = :roundId " +
            "ORDER BY am.matchNumber")
    List<AmericanoMatch> findByRoundIdOrderByMatchNumber(@Param("roundId") Long roundId);

    @Query("SELECT COUNT(am) FROM AmericanoMatch am WHERE am.round.id = :roundId " +
            "AND am.status = 'COMPLETED'")
    int countCompletedMatchesInRound(@Param("roundId") Long roundId);

    @Query("SELECT am FROM AmericanoMatch am WHERE am.tournament.id = :tournamentId " +
            "AND am.round.phase = :phase ORDER BY am.round.roundNumber, am.matchNumber")
    List<AmericanoMatch> findByTournamentIdAndPhase(
            @Param("tournamentId") Long tournamentId,
            @Param("phase") com.padle.core.padelcoreservice.model.enums.TournamentPhase phase);

    @Query("SELECT am FROM AmericanoMatch am WHERE am.round.id = :roundId " +
            "AND am.playoffStage = :stage ORDER BY am.matchNumber")
    List<AmericanoMatch> findByRoundIdAndPlayoffStage(
            @Param("roundId") Long roundId,
            @Param("stage") com.padle.core.padelcoreservice.model.enums.PlayoffStage stage);

    @Query("SELECT am FROM AmericanoMatch am WHERE am.tournament.id = :tournamentId " +
            "AND am.playoffStage = :stage ORDER BY am.matchNumber")
    List<AmericanoMatch> findByTournamentIdAndPlayoffStage(
            @Param("tournamentId") Long tournamentId,
            @Param("stage") com.padle.core.padelcoreservice.model.enums.PlayoffStage stage);

    @Query("SELECT DISTINCT am FROM AmericanoMatch am " +
            "WHERE (am.team1Player1 = :player OR am.team1Player2 = :player " +
            "OR am.team2Player1 = :player OR am.team2Player2 = :player) " +
            "AND am.tournament.id = :tournamentId " +
            "ORDER BY am.round.roundNumber, am.matchNumber")
    List<AmericanoMatch> findMatchesByPlayerAndTournament(
            @Param("player") PlayerPadel player,
            @Param("tournamentId") Long tournamentId);
}