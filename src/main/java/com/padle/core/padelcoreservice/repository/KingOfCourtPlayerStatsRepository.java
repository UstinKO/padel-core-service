package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.KingOfCourtPlayerStats;
import com.padle.core.padelcoreservice.model.PlayerPadel;
import com.padle.core.padelcoreservice.model.TournamentKingOfCourt;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KingOfCourtPlayerStatsRepository extends JpaRepository<KingOfCourtPlayerStats, Long> {

    Optional<KingOfCourtPlayerStats> findByTournamentKingAndPlayer(TournamentKingOfCourt tournamentKing, PlayerPadel player);

    @Query("SELECT s FROM KingOfCourtPlayerStats s WHERE s.tournamentKing.id = :kingId ORDER BY s.totalPoints DESC")
    List<KingOfCourtPlayerStats> findRanking(@Param("kingId") Long kingId);

    /**
     * Получить всех игроков турнира через статистику
     * @param kingId ID турнира King of Court
     * @return список игроков, участвующих в турнире
     */
    @Query("SELECT s.player FROM KingOfCourtPlayerStats s WHERE s.tournamentKing.id = :kingId ORDER BY s.player.id")
    List<PlayerPadel> findPlayersByTournamentKing(@Param("kingId") Long kingId);

    /**
     * Получить статистику всех игроков для конкретного турнира
     * @param kingId ID турнира King of Court
     * @return список статистики игроков
     */
    @Query("SELECT s FROM KingOfCourtPlayerStats s WHERE s.tournamentKing.id = :kingId ORDER BY s.totalPoints DESC")
    List<KingOfCourtPlayerStats> findAllByTournamentKingId(@Param("kingId") Long kingId);

    /**
     * Получить сырую статистику для отладки (native query с правильным именем таблицы)
     * @param kingId ID турнира King of Court
     * @return список Object[] с данными статистики
     */
    @Query(value = "SELECT * FROM king_of_court_player_stats_db WHERE tournament_king_id = :kingId", nativeQuery = true)
    List<Object[]> findRawStats(@Param("kingId") Long kingId);
}