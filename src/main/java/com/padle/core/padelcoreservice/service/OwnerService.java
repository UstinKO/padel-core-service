package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.OwnerDto;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.mapper.OwnerMapper;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.repository.OwnerRepository;
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

    private final OwnerRepository ownerRepository;
    private final OwnerMapper ownerMapper;

    @Transactional(readOnly = true)
    public OwnerDto getOwnerById(Long id, Owner currentOwner) {
        Owner owner = ownerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Owner not found with id: " + id));

        if (currentOwner.isSuperAdmin()) {
        } else if (currentOwner.getRole() == OwnerRole.ORGANIZER && currentOwner.getId().equals(owner.getId())) {
        } else {
            throw new AccessDeniedException("No tienes permiso para ver este perfil");
        }

        return ownerMapper.toDto(owner);
    }

    @Transactional(readOnly = true)
    public Optional<OwnerDto> getSuperAdmin() {
        return ownerRepository.findSuperAdmin()
                .map(ownerMapper::toDto);
    }

    // Метод для обновления данных владельца (для будущей админки)
    @Transactional
    public OwnerDto updateOwner(Long id, OwnerDto ownerDto, Owner currentOwner) {
        Owner owner = ownerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found with id: " + id));

        if (currentOwner.isSuperAdmin()) {
            owner.setFirstName(ownerDto.getFirstName());
            owner.setLastName(ownerDto.getLastName());
            owner.setPhone(ownerDto.getPhone());
            owner.setIsActive(ownerDto.getIsActive());
        } else if (currentOwner.getRole() == OwnerRole.ORGANIZER && currentOwner.getId().equals(owner.getId())) {
            owner.setFirstName(ownerDto.getFirstName());
            owner.setLastName(ownerDto.getLastName());
            owner.setPhone(ownerDto.getPhone());
        } else {
            throw new AccessDeniedException("No tienes permiso para editar este perfil");
        }

        Owner updatedOwner = ownerRepository.save(owner);
        log.info("Updated owner with id: {}", id);

        return ownerMapper.toDto(updatedOwner);
    }

    // ========== НОВЫЕ МЕТОДЫ ДЛЯ КОНТРОЛЛЕРА ==========

    @Transactional(readOnly = true)
    public long getTotalActiveOwners() {
        return ownerRepository.countByIsActiveTrue();
    }

    // ========== МЕТОД ДЛЯ ПОЛУЧЕНИЯ OWNER ПО EMAIL (возвращает Optional<Owner>) ==========
    @Transactional(readOnly = true)
    public Optional<Owner> findByEmail(String email) {
        log.debug("Finding owner by email: {}", email);
        return ownerRepository.findByEmail(email);
    }
}