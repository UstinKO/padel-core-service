package com.padle.core.padelcoreservice.support;

import com.padle.core.application.identity.OwnerAccount.Role;
import com.padle.core.padelcoreservice.mapper.OwnerPrincipalMapper;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.persistence.identity.OwnerAccountMapper;
import com.padle.core.persistence.identity.OwnerEntity;
import com.padle.core.persistence.identity.OwnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;

/** Создаёт строки аккаунтов для интеграционных тестов и возвращает principal для MVC. */
@Component
@RequiredArgsConstructor
public class OwnerFixtures {

    private final OwnerRepository owners;
    private final OwnerAccountMapper accounts;
    private final OwnerPrincipalMapper principals;

    public Owner save(Owner owner) {
        return principal(owners.save(entity(owner)));
    }

    public Owner saveAndFlush(Owner owner) {
        return principal(owners.saveAndFlush(entity(owner)));
    }

    public Optional<Owner> findById(Long id) {
        return owners.findById(id).map(this::principal);
    }

    public Optional<Owner> findByEmail(String email) {
        return owners.findByEmail(email).map(this::principal);
    }

    public void deleteById(Long id) {
        owners.deleteById(id);
    }

    public void deleteAllById(List<Long> ids) {
        owners.deleteAllById(ids);
    }

    public void flush() {
        owners.flush();
    }

    private Owner principal(OwnerEntity owner) {
        Owner principal = principals.toPrincipal(accounts.toAccount(owner));
        principal.setPassword(owner.getPassword());
        return principal;
    }

    private OwnerEntity entity(Owner owner) {
        return OwnerEntity.builder()
                .id(owner.getId())
                .email(owner.getEmail())
                .password(owner.getPassword())
                .firstName(owner.getFirstName())
                .lastName(owner.getLastName())
                .phone(owner.getPhone())
                .organizationName(owner.getOrganizationName())
                .role(Role.valueOf(owner.getRole().name()))
                .clubId(owner.getClubId())
                .clubIds(new HashSet<>(owner.getClubIds()))
                .createdAt(owner.getCreatedAt())
                .updatedAt(owner.getUpdatedAt())
                .lastLoginAt(owner.getLastLoginAt())
                .isActive(owner.getIsActive())
                .build();
    }
}
