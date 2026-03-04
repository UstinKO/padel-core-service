package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.Ranking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RankingRepository extends JpaRepository<Ranking, Long> {

    @Query("SELECT r FROM Ranking r WHERE r.player.id = :playerId")
    Optional<Ranking> findByPlayerId(@Param("playerId") Long playerId);

    @Query("SELECT r FROM Ranking r ORDER BY r.puntos DESC, r.partidosGanados DESC")
    List<Ranking> findAllOrderByPuntosDesc();

    @Query(value = "SELECT * FROM ranking_db ORDER BY puntos DESC LIMIT :limit", nativeQuery = true)
    List<Ranking> findTopRanking(@Param("limit") int limit);
}