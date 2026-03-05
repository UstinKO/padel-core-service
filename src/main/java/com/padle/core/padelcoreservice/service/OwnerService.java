package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.OwnerDto;
import com.padle.core.padelcoreservice.mapper.OwnerMapper;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.repository.OwnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OwnerService {

    private final OwnerRepository ownerRepository;
    private final OwnerMapper ownerMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Optional<OwnerDto> getOwnerById(Long id) {
        return ownerRepository.findById(id)
                .map(ownerMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<OwnerDto> getSuperAdmin() {
        return ownerRepository.findSuperAdmin()
                .map(ownerMapper::toDto);
    }

    // Метод для обновления данных владельца (для будущей админки)
    @Transactional
    public OwnerDto updateOwner(Long id, OwnerDto ownerDto) {
        Owner owner = ownerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Owner not found with id: " + id));

        owner.setFirstName(ownerDto.getFirstName());
        owner.setLastName(ownerDto.getLastName());
        owner.setPhone(ownerDto.getPhone());
        owner.setIsActive(ownerDto.getIsActive());

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