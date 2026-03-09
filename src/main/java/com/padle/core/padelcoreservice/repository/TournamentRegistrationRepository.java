package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentRegistrationRepository extends JpaRepository<TournamentRegistration, Long> {

    List<TournamentRegistration> findByTournamentId(Long tournamentId);

    @Query("SELECT tr FROM TournamentRegistration tr WHERE tr.tournament.id = :tournamentId AND tr.player.id = :playerId")
    Optional<TournamentRegistration> findByTournamentIdAndPlayerId(@Param("tournamentId") Long tournamentId, @Param("playerId") Long playerId);

    List<TournamentRegistration> findByTournamentIdAndStatus(Long tournamentId, RegistrationStatus status);

    @Query("SELECT COUNT(tr) FROM TournamentRegistration tr " +
            "WHERE tr.tournament.id = :tournamentId AND tr.status = :status")
    long countByTournamentIdAndStatus(@Param("tournamentId") Long tournamentId,
                                      @Param("status") RegistrationStatus status);

    /**
     * Найти всех в листе ожидания и приглашенных (для обработки при освобождении мест)
     */
    @Query("SELECT tr FROM TournamentRegistration tr " +
            "WHERE tr.tournament.id = :tournamentId AND tr.status IN ('WAITLIST', 'WAITLIST_INVITED') " +
            "ORDER BY " +
            "CASE WHEN tr.status = 'WAITLIST_INVITED' THEN 0 ELSE 1 END, " +
            "tr.waitlistPosition ASC")
    List<TournamentRegistration> findWaitlistWithInvitedByTournamentId(@Param("tournamentId") Long tournamentId);

    @Query("SELECT MAX(tr.waitlistPosition) FROM TournamentRegistration tr " +
            "WHERE tr.tournament.id = :tournamentId AND tr.status = 'WAITLIST'")
    Optional<Integer> findMaxWaitlistPosition(@Param("tournamentId") Long tournamentId);

    @Query("SELECT tr FROM TournamentRegistration tr " +
            "WHERE tr.player.id = :playerId AND tr.status IN ('CONFIRMED', 'WAITLIST')")
    List<TournamentRegistration> findActiveRegistrationsByPlayerId(@Param("playerId") Long playerId);

    @Query("SELECT COUNT(tr) FROM TournamentRegistration tr WHERE tr.status = 'WAITLIST' AND tr.isActive = true")
    long countTotalWaitlist();

    @Query("SELECT COUNT(tr) FROM TournamentRegistration tr WHERE tr.player.id = :playerId AND tr.status = :status")
    long countByPlayerIdAndStatus(@Param("playerId") Long playerId, @Param("status") RegistrationStatus status);

    // В TournamentRegistrationRepository добавить:

    List<TournamentRegistration> findByTournamentIdAndStatusOrderByWaitlistPositionAsc(
            Long tournamentId, RegistrationStatus status);

    List<TournamentRegistration> findByStatusAndInvitationExpiresAtBefore(
            RegistrationStatus status, LocalDateTime expiryTime);

    List<TournamentRegistration> findByTournamentIdOrderByPositionAscWaitlistPositionAsc(Long tournamentId);
}