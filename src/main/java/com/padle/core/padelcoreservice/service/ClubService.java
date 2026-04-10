package com.padle.core.padelcoreservice.service;

import com.padle.core.padelcoreservice.dto.ClubDto;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.mapper.ClubMapper;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ClubService {

    private final ClubRepository clubRepository;
    private final ClubMapper clubMapper;

    // ========== Публичные методы для просмотра ==========

    public List<ClubDto> getAllClubs() {
        return clubRepository.findAll().stream()
                .map(clubMapper::toDto)
                .collect(Collectors.toList());
    }

    public List<ClubDto> getActiveClubs() {
        return clubRepository.findByIsActiveTrue().stream()
                .map(clubMapper::toDto)
                .collect(Collectors.toList());
    }

    public Optional<ClubDto> getClubById(Long id) {
        return clubRepository.findById(id)
                .map(clubMapper::toDto);
    }

    public Optional<ClubDto> getClubByNombre(String nombre) {
        return clubRepository.findByNombre(nombre)
                .map(clubMapper::toDto);
    }

    public List<ClubDto> getClubsByZona(String zona) {
        return clubRepository.findByZonaCiudad(zona).stream()
                .map(clubMapper::toDto)
                .collect(Collectors.toList());
    }

    public List<ClubDto> searchClubs(String searchTerm) {
        return clubRepository.searchClubs(searchTerm).stream()
                .map(clubMapper::toDto)
                .collect(Collectors.toList());
    }

    // ========== Методы для админ-панели ==========

    @Transactional(readOnly = true)
    public List<ClubDto> getAllClubsForAdmin() {
        log.debug("Fetching all clubs for admin");
        return clubRepository.findAllByOrderByNombreAsc().stream()
                .map(clubMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ClubDto> getActiveClubsForAdmin() {
        log.debug("Fetching active clubs for admin");
        return clubRepository.findAllByIsActiveTrueOrderByNombreAsc().stream()
                .map(clubMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ClubDto getClubByIdForAdmin(Long id) {
        log.debug("Fetching club by id for admin: {}", id);
        Club club = clubRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Club not found with id: " + id));
        return clubMapper.toDto(club);
    }

    @Transactional
    public ClubDto createClub(ClubDto clubDto, Long createdBy) {
        log.info("Creating new club: {}", clubDto.getNombre());

        // Проверяем, существует ли уже клуб с таким названием
        if (clubRepository.existsByNombre(clubDto.getNombre())) {
            throw new RuntimeException("Ya existe un club con el nombre: " + clubDto.getNombre());
        }

        Club club = clubMapper.toEntity(clubDto);
        club.setCreatedBy(createdBy);
        club.setIsActive(true);

        Club savedClub = clubRepository.save(club);
        log.info("Created new club: {} with id: {}", savedClub.getNombre(), savedClub.getId());

        return clubMapper.toDto(savedClub);
    }

    @Transactional
    public Optional<ClubDto> updateClub(Long id, ClubDto clubDto) {
        return clubRepository.findById(id)
                .map(existingClub -> {
                    // Проверяем уникальность имени при изменении
                    if (!existingClub.getNombre().equals(clubDto.getNombre()) &&
                            clubRepository.existsByNombre(clubDto.getNombre())) {
                        throw new RuntimeException("Ya existe un club con el nombre: " + clubDto.getNombre());
                    }

                    updateClubFields(existingClub, clubDto);
                    Club updated = clubRepository.save(existingClub);
                    log.info("Updated club with id: {}", id);
                    return clubMapper.toDto(updated);
                });
    }

    @Transactional
    public boolean deleteClub(Long id) {
        return clubRepository.findById(id)
                .map(club -> {
                    club.setIsActive(false);
                    clubRepository.save(club);
                    log.info("Soft deleted club with id: {}", id);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public boolean hardDeleteClub(Long id) {
        if (clubRepository.existsById(id)) {
            clubRepository.deleteById(id);
            log.info("Hard deleted club with id: {}", id);
            return true;
        }
        return false;
    }

    @Transactional
    public void toggleClubStatus(Long id) {
        log.info("Toggling club status with id: {}", id);

        Club club = clubRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Club not found with id: " + id));

        club.setIsActive(!club.getIsActive());
        clubRepository.save(club);

        log.info("Club status toggled. New active status: {}", club.getIsActive());
    }

    // ========== Вспомогательные методы ==========

    private void updateClubFields(Club existing, ClubDto dto) {
        existing.setNombre(dto.getNombre());
        existing.setDireccion(dto.getDireccion());
        existing.setZonaCiudad(dto.getZonaCiudad());
        existing.setTelefonoContacto(dto.getTelefonoContacto());
        existing.setEmailContacto(dto.getEmailContacto());
        existing.setMapaUrl(dto.getMapaUrl());
        existing.setWebsiteUrl(dto.getWebsiteUrl());
        existing.setDescripcion(dto.getDescripcion());
        existing.setLogoUrl(dto.getLogoUrl());
    }
}