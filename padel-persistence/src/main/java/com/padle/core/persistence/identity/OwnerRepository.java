package com.padle.core.persistence.identity;

import com.padle.core.application.identity.OwnerAccount.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OwnerRepository extends JpaRepository<OwnerEntity, Long> {

    Optional<OwnerEntity> findByEmail(String email);

    @Query("SELECT o.id FROM Owner o WHERE o.email = :email")
    Optional<Long> findIdByEmail(@Param("email") String email);

    Optional<OwnerEntity> findByRoleAndIsActiveTrue(Role role);

    long countByIsActiveTrue();

    List<OwnerEntity> findByRole(Role role);

    List<OwnerEntity> findByClubId(Long clubId);
}
