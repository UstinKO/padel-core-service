package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.TournamentRegistration;
import com.padle.core.padelcoreservice.model.enums.RegistrationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    @Query("SELECT COUNT(tr) FROM TournamentRegistration tr " +
            "WHERE tr.tournament.id = :tournamentId AND tr.status IN :statuses AND tr.isActive = true")
    long countByTournamentIdAndStatusIn(@Param("tournamentId") Long tournamentId,
                                        @Param("statuses") List<RegistrationStatus> statuses);

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

    @Query(nativeQuery = true, value =
            "SELECT COUNT(DISTINCT " +
            "LEAST(tr.player_id, COALESCE(tr.partner_id, tr.player_id))::text || '-' || " +
            "GREATEST(tr.player_id, COALESCE(tr.partner_id, tr.player_id))::text) " +
            "FROM tournament_registrations_db tr " +
            "WHERE tr.tournament_id = :tournamentId " +
            "AND tr.status IN ('CONFIRMED', 'PAIR_REGISTERED', 'PARTNER_INVITED') " +
            "AND tr.is_double_registration = true " +
            "AND tr.is_active = true " +
            "AND tr.main_player_id IS NOT NULL " +
            "AND tr.player_id = tr.main_player_id")
    long countConfirmedPairs(@Param("tournamentId") Long tournamentId);

    /**
     * Считает уникальные пары в листе ожидания.
     * Аналогично countConfirmedPairs — группируем по mainPlayerId.
     */
    @Query("SELECT COUNT(DISTINCT tr.mainPlayerId) FROM TournamentRegistration tr " +
            "WHERE tr.tournament.id = :tournamentId " +
            "AND tr.status = 'WAITLIST' " +
            "AND tr.isDoubleRegistration = true " +
            "AND tr.isActive = true " +
            "AND tr.mainPlayerId IS NOT NULL")
    long countUniquePairsInWaitlist(@Param("tournamentId") Long tournamentId);

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
     * Подсчет всех активных регистраций (для индивидуальных турниров)
     */
    @Query("SELECT COUNT(t) FROM TournamentRegistration t " +
            "WHERE t.tournament.id = :tournamentId " +
            "AND t.isActive = true")
    long countActiveRegistrations(@Param("tournamentId") Long tournamentId);

    /**
     * Подсчет подтвержденных регистраций (для индивидуальных турниров)
     */
    @Query("SELECT COUNT(t) FROM TournamentRegistration t " +
            "WHERE t.tournament.id = :tournamentId " +
            "AND t.isActive = true " +
            "AND t.status = 'CONFIRMED'")
    long countConfirmedRegistrations(@Param("tournamentId") Long tournamentId);

    /**
     * Проверить, есть ли у игрока активная регистрация
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM TournamentRegistration t " +
            "WHERE t.player.id = :playerId AND t.tournament.id = :tournamentId AND t.isActive = true")
    boolean existsActiveRegistration(@Param("playerId") Long playerId, @Param("tournamentId") Long tournamentId);

    /**
     * Найти все регистрации для партнера (где он является партнером)
     */
    @Query("SELECT t FROM TournamentRegistration t " +
            "WHERE t.partner.id = :partnerId OR " +
            "(t.mainPlayerId = :partnerId AND t.player.id != :partnerId)")
    List<TournamentRegistration> findPartnerRegistrations(@Param("partnerId") Long partnerId);

    /**
     * Подсчет уникальных пар (группировка по mainPlayerId) для парных турниров
     * Учитываем только CONFIRMED и PARTNER_INVITED (где приглашение действительно)
     */
    @Query("SELECT COUNT(DISTINCT t.mainPlayerId) FROM TournamentRegistration t " +
            "WHERE t.tournament.id = :tournamentId " +
            "AND t.isActive = true " +
            "AND t.status IN ('CONFIRMED', 'PARTNER_INVITED')")
    long countUniquePairs(@Param("tournamentId") Long tournamentId);

    List<TournamentRegistration> findByTournamentIdAndStatusOrderByPositionAsc(
            Long tournamentId, RegistrationStatus status);

    // В TournamentRegistrationRepository
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT tr FROM TournamentRegistration tr WHERE tr.tournament.id = :tournamentId AND tr.player.id = :playerId")
    Optional<TournamentRegistration> findByTournamentIdAndPlayerIdWithLock(
            @Param("tournamentId") Long tournamentId,
            @Param("playerId") Long playerId
    );

    // TournamentRegistrationRepository.java
    @Query("SELECT tr FROM TournamentRegistration tr " +
            "WHERE tr.tournament.id = :tournamentId " +
            "AND tr.player.id = :playerId " +
            "AND tr.isActive = true")
    Optional<TournamentRegistration> findByTournamentIdAndPlayerIdAndIsActiveTrue(
            @Param("tournamentId") Long tournamentId,
            @Param("playerId") Long playerId);

    @Query("SELECT tr FROM TournamentRegistration tr " +
            "WHERE tr.tournament.id = :tournamentId " +
            "AND tr.player.id = :playerId " +
            "AND tr.isActive = false")
    Optional<TournamentRegistration> findByTournamentIdAndPlayerIdAndIsActiveFalse(
            @Param("tournamentId") Long tournamentId,
            @Param("playerId") Long playerId);
}