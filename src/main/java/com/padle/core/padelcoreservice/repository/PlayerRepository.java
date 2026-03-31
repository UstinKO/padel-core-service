package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.PlayerPadel;
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
}