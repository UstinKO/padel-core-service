package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OwnerRepository extends JpaRepository<Owner, Long> {

    Optional<Owner> findByEmail(String email);

    // Поиск суперадмина по роли
    @Query("SELECT o FROM Owner o WHERE o.role = :role AND o.isActive = true")
    Optional<Owner> findByRoleAndIsActiveTrue(@Param("role") OwnerRole role);

    // Для обратной совместимости - ищем суперадмина
    default Optional<Owner> findSuperAdmin() {
        return findByRoleAndIsActiveTrue(OwnerRole.SUPER_ADMIN);
    }

    long countByIsActiveTrue();

    // Поиск по роли
    List<Owner> findByRole(OwnerRole role);
}