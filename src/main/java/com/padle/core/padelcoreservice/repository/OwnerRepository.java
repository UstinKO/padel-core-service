package com.padle.core.padelcoreservice.repository;

import com.padle.core.padelcoreservice.model.Owner;
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

    @Query("SELECT o FROM Owner o WHERE o.isSuperAdmin = true AND o.isActive = true")
    Optional<Owner> findSuperAdmin();

    long countByIsActiveTrue();
}