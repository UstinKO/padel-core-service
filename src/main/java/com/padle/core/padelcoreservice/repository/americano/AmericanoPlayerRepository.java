package com.padle.core.padelcoreservice.repository.americano;

import com.padle.core.padelcoreservice.model.americano.AmericanoPlayer;
import com.padle.core.padelcoreservice.model.enums.AmericanoPlayerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AmericanoPlayerRepository extends JpaRepository<AmericanoPlayer, Long> {

    List<AmericanoPlayer> findByTournamentId(Long tournamentId);

    Optional<AmericanoPlayer> findByTournamentIdAndPlayerId(Long tournamentId, Long playerId);

    List<AmericanoPlayer> findByTournamentIdAndStatusOrderByTotalScoreDesc(
            Long tournamentId, AmericanoPlayerStatus status);

    @Query("SELECT ap FROM AmericanoPlayer ap WHERE ap.tournament.id = :tournamentId " +
            "AND ap.status = 'ACTIVE' ORDER BY ap.totalScore DESC, (ap.pointsScored - ap.pointsConceded) DESC")
    List<AmericanoPlayer> findActiveRanking(@Param("tournamentId") Long tournamentId);

    @Query("SELECT COUNT(ap) FROM AmericanoPlayer ap WHERE ap.tournament.id = :tournamentId " +
            "AND ap.status = 'ACTIVE'")
    int countActivePlayers(@Param("tournamentId") Long tournamentId);

    @Query("SELECT ap FROM AmericanoPlayer ap WHERE ap.tournament.id = :tournamentId " +
            "AND ap.status = 'ACTIVE' ORDER BY ap.initialPosition")
    List<AmericanoPlayer> findActivePlayersByInitialPosition(@Param("tournamentId") Long tournamentId);

    boolean existsByTournamentIdAndPlayerId(Long tournamentId, Long playerId);

    // Методы для сортировки по очкам
    List<AmericanoPlayer> findByTournamentIdOrderByTotalScoreDesc(Long tournamentId);
    List<AmericanoPlayer> findByTournamentIdOrderByTotalScoreAsc(Long tournamentId);

    // Методы для сортировки по победам
    List<AmericanoPlayer> findByTournamentIdOrderByMatchesWonDesc(Long tournamentId);
    List<AmericanoPlayer> findByTournamentIdOrderByMatchesWonAsc(Long tournamentId);

    // Метод для поиска по статусу (нужен в generateAllRounds)
    List<AmericanoPlayer> findByTournamentIdAndStatus(Long tournamentId, AmericanoPlayerStatus status);
}