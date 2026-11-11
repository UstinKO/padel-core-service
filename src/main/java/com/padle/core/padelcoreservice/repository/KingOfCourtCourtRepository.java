package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.KingOfCourtCourt;
import com.padle.core.padelcoreservice.model.KingOfCourtRound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KingOfCourtCourtRepository extends JpaRepository<KingOfCourtCourt, Long> {

    Optional<KingOfCourtCourt> findByRoundAndCourtNumber(KingOfCourtRound round, Integer courtNumber);

    @Query("SELECT CASE WHEN COUNT(c) = 0 THEN true ELSE false END FROM KingOfCourtCourt c WHERE c.round.id = :roundId AND c.result IS NULL")
    boolean allResultsEntered(@Param("roundId") Long roundId);
    @EntityGraph(attributePaths = {"players", "result", "result.winner1", "result.winner2", "result.loser1", "result.loser2"})
    @Query("select distinct c from KingOfCourtCourt c where c.round.tournamentKing.id = :kingId")
    List<KingOfCourtCourt> findPublicPlayers(@Param("kingId") Long kingId);

    @EntityGraph(attributePaths = {"teams", "teams.player1", "teams.player2", "result"})
    @Query("select distinct c from KingOfCourtCourt c where c.round.tournamentKing.id = :kingId")
    List<KingOfCourtCourt> findPublicTeams(@Param("kingId") Long kingId);
}