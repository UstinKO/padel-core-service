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
            "WHERE tr.player.id = :playerId AND tr.isActive = true")
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

    @Query("SELECT COUNT(DISTINCT tr.mainPlayerId) FROM TournamentRegistration tr " +
            "WHERE tr.tournament.id = :tournamentId AND tr.status IN ('CONFIRMED', 'PAIR_REGISTERED') " +
            "AND tr.isDoubleRegistration = true")
    long countConfirmedPairs(@Param("tournamentId") Long tournamentId);

    @Query("SELECT tr FROM TournamentRegistration tr " +
            "WHERE tr.player.id = :playerId AND tr.isDoubleRegistration = true " +
            "AND tr.isActive = true AND tr.status IN ('CONFIRMED', 'PENDING_PARTNER', 'PAIR_REGISTERED')")
    List<TournamentRegistration> findActiveDoubleRegistrationsByPlayerId(@Param("playerId") Long playerId);

    Optional<TournamentRegistration> findByPartnerRegistrationToken(String token);

    @Query("SELECT COUNT(DISTINCT r.mainPlayerId) FROM TournamentRegistration r " +
            "WHERE r.tournament.id = :tournamentId " +
            "AND r.isActive = true " +
            "AND r.isDoubleRegistration = true " +
            "AND r.status IN ('CONFIRMED', 'PARTNER_INVITED', 'PENDING_PARTNER', 'PAIR_REGISTERED')")
    long countActivePairsByTournamentId(@Param("tournamentId") Long tournamentId);

    @Query("SELECT COUNT(r) FROM TournamentRegistration r " +
            "WHERE r.tournament.id = :tournamentId AND r.isActive = true")
    long countByTournamentIdAndIsActiveTrue(@Param("tournamentId") Long tournamentId);

    /**
     * Подсчет количества активных одиночных регистраций в турнире (не парных)
     * Используется для правильного подсчета занятых мест в парных турнирах
     *
     * @param tournamentId ID турнира
     * @return количество активных одиночных регистраций
     */
    @Query("SELECT COUNT(r) FROM TournamentRegistration r " +
            "WHERE r.tournament.id = :tournamentId " +
            "AND r.isActive = true " +
            "AND r.isDoubleRegistration = false")
    long countByTournamentIdAndIsDoubleRegistrationFalseAndIsActiveTrue(@Param("tournamentId") Long tournamentId);

    /**
     * Считаем занятые места в турнире
     * Простая логика:
     * - Если запись имеет partner (ссылку на игрока) или partnerFirstName - это парная регистрация
     * - Каждая такая запись занимает 2 места, но мы считаем их как 1 пару
     */
    @Query("SELECT COUNT(DISTINCT CASE " +
            "WHEN r.mainPlayerId IS NOT NULL THEN r.mainPlayerId " +
            "ELSE r.player.id END) * 2 " +
            "FROM TournamentRegistration r " +
            "WHERE r.tournament.id = :tournamentId " +
            "AND r.isActive = true " +
            "AND (r.partner IS NOT NULL OR r.partnerFirstName IS NOT NULL)")
    long countSpotsOccupiedByPairs(@Param("tournamentId") Long tournamentId);

    /**
     * Считаем занятые места одиночными регистрациями
     */
    @Query("SELECT COUNT(r) FROM TournamentRegistration r " +
            "WHERE r.tournament.id = :tournamentId " +
            "AND r.isActive = true " +
            "AND r.partner IS NULL " +
            "AND r.partnerFirstName IS NULL")
    long countSpotsOccupiedBySingles(@Param("tournamentId") Long tournamentId);

    /**
     * Считаем количество уникальных пар в турнире
     */
    @Query("SELECT COUNT(DISTINCT CASE " +
            "WHEN r.mainPlayerId IS NOT NULL THEN r.mainPlayerId " +
            "ELSE r.player.id END) " +
            "FROM TournamentRegistration r " +
            "WHERE r.tournament.id = :tournamentId " +
            "AND r.isActive = true " +
            "AND r.status = 'CONFIRMED' " +
            "AND (r.partner IS NOT NULL OR r.partnerFirstName IS NOT NULL)")
    long countUniquePairs(@Param("tournamentId") Long tournamentId);
}