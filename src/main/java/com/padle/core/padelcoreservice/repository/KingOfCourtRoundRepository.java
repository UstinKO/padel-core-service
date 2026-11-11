package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.KingOfCourtRound;
import com.padle.core.padelcoreservice.model.TournamentKingOfCourt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KingOfCourtRoundRepository extends JpaRepository<KingOfCourtRound, Long> {

    List<KingOfCourtRound> findByTournamentKingOrderByRoundNumberAsc(TournamentKingOfCourt tournamentKing);
    @EntityGraph(attributePaths = {"courts", "courts.result"})
    @Query("select distinct r from KingOfCourtRound r where r.tournamentKing.id = :kingId order by r.roundNumber")
    List<KingOfCourtRound> findPublicRounds(@Param("kingId") Long kingId);
}