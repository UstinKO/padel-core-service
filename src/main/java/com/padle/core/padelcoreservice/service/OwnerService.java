package com.padle.core.padelcoreservice.service;

import com.padle.core.application.identity.OwnerAccount;
import com.padle.core.application.identity.OwnerAccounts;
import com.padle.core.padelcoreservice.dto.OwnerDto;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.mapper.OwnerMapper;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OwnerService {

    private final OwnerAccounts owners;
    private final OwnerMapper ownerMapper;

    @Transactional(readOnly = true)
    public OwnerDto getOwnerById(Long id, Owner currentOwner) {
        OwnerAccount owner = owners.findById(id).orElseThrow(() -> new ResourceNotFoundException("Owner not found with id: " + id));

        if (!canManageProfile(currentOwner, owner.id())) {
            throw new AccessDeniedException("No tienes permiso para ver este perfil");
        }

        return ownerMapper.toDto(owner);
    }

    @Transactional(readOnly = true)
    public Optional<OwnerDto> getSuperAdmin() {
        return owners.findActiveSuperAdmin()
                .map(ownerMapper::toDto);
    }

    // Метод для обновления данных владельца (для будущей админки)
    @Transactional
    public OwnerDto updateOwner(Long id, OwnerDto ownerDto, Owner currentOwner) {
        OwnerAccount owner = owners.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found with id: " + id));

        if (!canManageProfile(currentOwner, owner.id())) {
            throw new AccessDeniedException("No tienes permiso para editar este perfil");
        }

        Boolean active = currentOwner.isSuperAdmin() ? ownerDto.getIsActive() : owner.isActive();
        var profile = new OwnerAccounts.Profile(ownerDto.getFirstName(), ownerDto.getLastName(), ownerDto.getPhone(), active);
        OwnerAccount updatedOwner = owners.updateProfile(id, profile);
        log.info("Updated owner with id: {}", id);

        return ownerMapper.toDto(updatedOwner);
    }

    @Transactional(readOnly = true)
    public long getTotalActiveOwners() {
        return owners.countActive();
    }

    @Transactional(readOnly = true)
    public Optional<Long> findIdByEmail(String email) {
        log.debug("Finding owner by email: {}", email);
        return owners.findIdByEmail(email);
    }

    private boolean canManageProfile(Owner actor, Long id) {
        return actor.isSuperAdmin() || actor.getRole() == OwnerRole.ORGANIZER && actor.getId().equals(id);
    }
}
