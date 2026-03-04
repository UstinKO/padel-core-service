package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentKingOfCourt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentKingOfCourtRepository extends JpaRepository<TournamentKingOfCourt, Long> {

    // Возвращаем список, так как для одного турнира может быть несколько KingOfCourt
    List<TournamentKingOfCourt> findAllByTournamentId(Long tournamentId);

    @Query("SELECT CASE WHEN COUNT(k) > 0 THEN true ELSE false END FROM TournamentKingOfCourt k WHERE k.tournament.id = :tournamentId AND k.isActive = true")
    boolean existsActiveByTournamentId(@Param("tournamentId") Long tournamentId);

    List<TournamentKingOfCourt> findAllByTournamentIdAndIsActiveTrue(Long tournamentId);
}