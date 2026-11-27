package com.padle.core.persistence.identity;

import com.padle.core.application.identity.OwnerAccount;
import com.padle.core.application.identity.OwnerAccounts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JpaOwnerAccounts implements OwnerAccounts {

    private final OwnerRepository owners;
    private final OwnerAccountMapper mapper;

    @Override
    public Optional<OwnerAccount> findById(Long id) {
        return owners.findById(id).map(mapper::toAccount);
    }

    @Override
    public Optional<Long> findIdByEmail(String email) {
        return owners.findIdByEmail(email);
    }

    @Override
    public Optional<Login> findForLogin(String email) {
        return owners.findByEmail(email).map(owner -> new Login(mapper.toAccount(owner), owner.getPassword()));
    }

    @Override
    public Optional<OwnerAccount> findActiveSuperAdmin() {
        return owners.findByRoleAndIsActiveTrue(OwnerAccount.Role.SUPER_ADMIN).map(mapper::toAccount);
    }

    @Override
    public long countActive() {
        return owners.countByIsActiveTrue();
    }

    @Override
    public List<OwnerAccount> findByRole(OwnerAccount.Role role) {
        return owners.findByRole(role).stream().map(mapper::toAccount).toList();
    }

    @Override
    public List<OwnerAccount> findAll() {
        return owners.findAll().stream().map(mapper::toAccount).toList();
    }

    @Override
    public List<OwnerAccount> findAllById(List<Long> ids) {
        return owners.findAllById(ids).stream().map(mapper::toAccount).toList();
    }

    @Override
    @Transactional
    public OwnerAccount updateProfile(Long id, Profile profile) {
        OwnerEntity owner = owners.getReferenceById(id);
        mapper.updateProfile(profile, owner);
        return mapper.toAccount(owner);
    }

    @Override
    @Transactional
    public OwnerAccount updateOrganizationName(Long id, String name) {
        OwnerEntity owner = owners.getReferenceById(id);
        owner.setOrganizationName(name);
        return mapper.toAccount(owner);
    }

    @Override
    @Transactional
    public void replaceClubAssignments(Long id, Long primaryClubId, Set<Long> clubIds) {
        OwnerEntity owner = owners.getReferenceById(id);
        owner.setClubId(primaryClubId);
        owner.setClubIds(new HashSet<>(clubIds));
    }
}
