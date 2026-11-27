package com.padle.core.padelcoreservice.service;

import com.padle.core.application.clubs.ClubAdminReadPort;
import com.padle.core.application.clubs.ClubAdminWritePort;
import com.padle.core.application.clubs.ClubProfile;
import com.padle.core.application.clubs.ClubTournamentUsage;
import com.padle.core.padelcoreservice.dto.ClubDto;
import com.padle.core.padelcoreservice.exception.InvalidStateException;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.mapper.ClubDtoMapper;
import com.padle.core.padelcoreservice.model.Owner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ClubService {

    private final ClubAdminReadPort clubReads;
    private final ClubAdminWritePort clubWrites;
    private final ClubDtoMapper clubMapper;
    private final ClubTournamentUsage tournaments;

    // ========== Публичные методы для просмотра ==========

    public List<ClubDto> getAllClubs() {
        return clubMapper.toDto(clubReads.findAll());
    }

    public List<ClubDto> getActiveClubs() {
        return clubMapper.toDto(clubReads.findActive());
    }

    public Optional<ClubDto> getClubById(Long id) {
        return clubReads.findProfileById(id).map(clubMapper::toDto);
    }

    public Optional<ClubDto> getClubByNombre(String nombre) {
        return clubReads.findByName(nombre).map(clubMapper::toDto);
    }

    public List<ClubDto> getClubsByZona(String zona) {
        return clubMapper.toDto(clubReads.findByCity(zona));
    }

    public List<ClubDto> searchClubs(String searchTerm) {
        return clubMapper.toDto(clubReads.search(searchTerm));
    }

    // ========== Методы для админ-панели ==========

    @Transactional(readOnly = true)
    public List<ClubDto> getAllClubsForAdmin() {
        log.debug("Fetching all clubs for admin");
        return clubMapper.toDto(clubReads.findAllOrderedByName());
    }

    @Transactional(readOnly = true)
    public List<ClubDto> getActiveClubsForAdmin() {
        log.debug("Fetching active clubs for admin");
        return clubMapper.toDto(clubReads.findActiveOrderedByName());
    }

    @Transactional(readOnly = true)
    public ClubDto getClubByIdForAdmin(Long id) {
        log.debug("Fetching club by id for admin: {}", id);
        return clubMapper.toDto(requireClub(id));
    }

    @Transactional
    public ClubDto createClub(ClubDto clubDto, Long createdBy) {
        log.info("Creating new club: {}", clubDto.getNombre());

        // Проверяем, существует ли уже клуб с таким названием
        if (clubReads.existsByName(clubDto.getNombre())) {
            throw new RuntimeException("Ya existe un club con el nombre: " + clubDto.getNombre());
        }

        ClubProfile savedClub = clubWrites.create(clubMapper.toFields(clubDto), createdBy);
        log.info("Created new club: {} with id: {}", savedClub.fields().name(), savedClub.id());

        return clubMapper.toDto(savedClub);
    }

    @Transactional
    public ClubDto updateClub(Long id, ClubDto clubDto, Owner currentOwner) {
        ClubProfile club = requireClub(id);

        if (!club.fields().name().equals(clubDto.getNombre()) &&
                clubReads.existsByName(clubDto.getNombre())) {
            throw new RuntimeException("Ya existe un club con el nombre: " + clubDto.getNombre());
        }

        if (!currentOwner.isSuperAdmin()) {
            throw new AccessDeniedException("No tienes permiso para editar este Club");
        }

        ClubProfile updated = clubWrites.update(id, clubMapper.toFields(clubDto));
        log.info("Updated club with id: {}", id);
        return clubMapper.toDto(updated);
    }

    @Transactional
    public void deleteClub(Long id, Owner currentOwner) {
        requireClub(id);

        if (!currentOwner.isSuperAdmin()) {
            throw new AccessDeniedException("No tienes permiso para eliminar este Club");
        }

        clubWrites.setActive(id, false);
        log.info("Soft deleted club with id: {}", id);
    }

    @Transactional
    public void hardDeleteClub(Long id, Owner currentOwner) {

        requireClub(id);

        if (!currentOwner.isSuperAdmin()) {
            throw new AccessDeniedException("No tienes permiso para cambiar el estado de los clubes");
        }

        if (tournaments.existsByClubId(id)) {
            throw new InvalidStateException("No se puede eliminar el club porque tiene torneos asociados");
        }
        clubWrites.delete(id);
        log.info("Hard deleted club with id: {}", id);
    }

    @Transactional
    public void toggleClubStatus(Long id) {
        log.info("Toggling club status with id: {}", id);

        boolean active = !requireClub(id).active();
        clubWrites.setActive(id, active);
        log.info("Club status toggled. New active status: {}", active);
    }

    private ClubProfile requireClub(Long id) {
        return clubReads.findProfileById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Club not found with id: " + id));
    }
}
