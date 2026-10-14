package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.PlayerPadel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlayerRepository extends JpaRepository<PlayerPadel, Long> {

    Optional<PlayerPadel> findByEmail(String email);

    Optional<PlayerPadel> findByTelefono(String telefono);  // Добавляем этот метод

    boolean existsByEmail(String email);

    boolean existsByTelefono(String telefono);

    Optional<PlayerPadel> findByCodigoConfirmacion(String codigoConfirmacion);

    long countByActivoTrue();

    @Query("SELECT p FROM PlayerPadel p ORDER BY p.fechaRegistro DESC LIMIT :limit")
    List<PlayerPadel> findTopByOrderByFechaRegistroDesc(@Param("limit") int limit);

    @Query("SELECT p FROM PlayerPadel p ORDER BY p.fechaRegistro DESC")
    List<PlayerPadel> findAllByOrderByFechaRegistroDesc();

    Optional<PlayerPadel> findByTelegramUsername(String telegramUsername);

    boolean existsByTelegramUsernameIgnoreCase(String telegramUsername);

    @Query("SELECT p FROM PlayerPadel p WHERE p.activo = true AND p.id != :excludeId " +
           "AND p.id NOT IN (" +
           "  SELECT tr.player.id FROM TournamentRegistration tr " +
           "  WHERE tr.tournament.id = :tournamentId AND tr.isActive = true" +
           ") " +
           "AND (LOWER(p.nombre) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "  OR LOWER(p.apellido) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "  OR LOWER(p.email) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<PlayerPadel> searchPartnerCandidates(@Param("query") String query,
                                              @Param("tournamentId") Long tournamentId,
                                              @Param("excludeId") Long excludeId,
                                              Pageable pageable);

    // LFPT-0561: поиск активных игроков по имени/фамилии/телефону/email для формы
    // «Добавить команду» Team Playoff — в отличие от searchPartnerCandidates, не исключает
    // игроков с уже активной регистрацией на турнир (и не требует tournamentId), а также ищет
    // по телефону — это критерий, который клиент явно запросил.
    @Query("SELECT p FROM PlayerPadel p WHERE p.activo = true " +
           "AND (LOWER(p.nombre) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "  OR LOWER(p.apellido) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "  OR LOWER(p.email) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "  OR p.telefono LIKE CONCAT('%', :query, '%')) " +
           "ORDER BY p.nombre, p.apellido")
    List<PlayerPadel> searchActivePlayers(@Param("query") String query, Pageable pageable);
}