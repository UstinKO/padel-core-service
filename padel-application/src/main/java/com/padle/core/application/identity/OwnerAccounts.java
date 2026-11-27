package com.padle.core.application.identity;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface OwnerAccounts {

    Optional<OwnerAccount> findById(Long id);

    Optional<Long> findIdByEmail(String email);

    Optional<Login> findForLogin(String email);

    Optional<OwnerAccount> findActiveSuperAdmin();

    long countActive();

    List<OwnerAccount> findByRole(OwnerAccount.Role role);

    List<OwnerAccount> findAll();

    List<OwnerAccount> findAllById(List<Long> ids);

    OwnerAccount updateProfile(Long id, Profile profile);

    OwnerAccount updateOrganizationName(Long id, String name);

    void replaceClubAssignments(Long id, Long primaryClubId, Set<Long> clubIds);

    record Profile(String firstName, String lastName, String phone, Boolean isActive) {}

    record Login(OwnerAccount account, String passwordHash) {
        @Override
        public String toString() {
            return "Login[ownerId=" + account.id() + "]";
        }
    }
}
