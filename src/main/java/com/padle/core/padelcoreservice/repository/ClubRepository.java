package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.Club;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClubRepository extends JpaRepository<Club, Long> {

    Optional<Club> findByNombre(String nombre);

    List<Club> findByZonaCiudad(String zonaCiudad);

    List<Club> findByIsActiveTrue();

    // Добавленные методы для админ-панели
    List<Club> findAllByOrderByNombreAsc();

    List<Club> findAllByIsActiveTrueOrderByNombreAsc();

    @Query("SELECT c FROM Club c WHERE " +
            "LOWER(c.nombre) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
            "LOWER(c.zonaCiudad) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<Club> searchClubs(@Param("searchTerm") String searchTerm);

    boolean existsByNombre(String nombre);
}