package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.Tournament;
import com.padle.core.padelcoreservice.model.enums.GenderFormat;
import com.padle.core.padelcoreservice.model.enums.TournamentStatus;
import com.padle.core.padelcoreservice.model.enums.TournamentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    List<Tournament> findByClubId(Long clubId);

    List<Tournament> findByEstado(TournamentStatus estado);

    List<Tournament> findByIsActiveTrue();

    @Query("SELECT t FROM Tournament t WHERE " +
            "(:clubId IS NULL OR t.clubId = :clubId) AND " +
            "(:genero IS NULL OR t.generoFormato = :genero) AND " +
            "(:nivel IS NULL OR t.categoriaNivel = :nivel) AND " +
            "(:tipo IS NULL OR t.tipo = :tipo) AND " +
            "(:estado IS NULL OR t.estado = :estado) AND " +
            "t.isActive = true " +
            "ORDER BY t.fechaInicio ASC")
    List<Tournament> searchTournaments(@Param("clubId") Long clubId,
                                       @Param("genero") GenderFormat genero,
                                       @Param("nivel") String nivel,
                                       @Param("tipo") TournamentType tipo,
                                       @Param("estado") TournamentStatus estado);

    // ========== НОВЫЕ МЕТОДЫ ==========

    long countByIsActiveTrue();

    @Query("SELECT t FROM Tournament t ORDER BY t.createdAt DESC LIMIT :limit")
    List<Tournament> findTopByOrderByCreatedAtDesc(@Param("limit") int limit);

    // Только активные предстоящие турниры
    @Query("SELECT t FROM Tournament t WHERE t.estado = :estado AND t.fechaInicio >= CURRENT_DATE AND t.isActive = true ORDER BY t.fechaInicio ASC")
    List<Tournament> findUpcomingActiveTournaments(@Param("estado") TournamentStatus estado);

    @Query("SELECT t FROM Tournament t WHERE t.isActive = true AND " +
            "(t.estado = 'REGISTRO_ABIERTO' OR t.estado = 'PUBLICADO') " +
            "AND t.fechaInicio >= CURRENT_DATE " +
            "ORDER BY t.fechaInicio ASC")
    List<Tournament> findActiveForHome();

    @Query("SELECT t FROM Tournament t WHERE t.estado IN :estados AND t.isActive = true")
    List<Tournament> findByEstadoInAndIsActiveTrue(@Param("estados") List<TournamentStatus> estados);
}