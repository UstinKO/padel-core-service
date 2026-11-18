package com.padle.core.padelcoreservice.service;

import java.util.Collections;
import com.padle.core.padelcoreservice.dto.ClubDto;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.mapper.ClubMapper;
import com.padle.core.padelcoreservice.model.Club;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.model.enums.OwnerRole;
import com.padle.core.padelcoreservice.repository.ClubRepository;
import com.padle.core.padelcoreservice.repository.OwnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubAccountService {
    private final OwnerRepository owners;
    private final ClubRepository clubs;
    private final ClubMapper mapper;

    public Set<Long> assignedClubIds(Owner account) {
        // Сессия может быть открыта до изменения назначений: читаем актуальные права из базы.
        Owner fresh = requireAccount(account.getId());
        return Stream.concat(fresh.getClubIds().stream(), Stream.ofNullable(fresh.getClubId()))
                .collect(Collectors.toUnmodifiableSet());
    }

    public List<ClubDto> availableClubs(Owner account) {
        if (!account.isClubAdmin()) {
            return clubs.findAllByIsActiveTrueOrderByNombreAsc().stream().map(mapper::toDto).toList();
        }
        Set<Long> assigned = assignedClubIds(account);
        return clubs.findAllByIsActiveTrueOrderByNombreAsc().stream()
                .filter(club -> assigned.contains(club.getId())).map(mapper::toDto).toList();
    }

    public void assertCanCreateAt(Owner account, Long clubId) {
        if (account.isClubAdmin() && (clubId == null || !assignedClubIds(account).contains(clubId)
                || !clubs.existsByIdAndIsActiveTrue(clubId))) {
            throw new AccessDeniedException("club_access.denied");
        }
    }

    public void assertCanViewClub(Owner account, Long clubId) {
        if (account.isClubAdmin() && !assignedClubIds(account).contains(clubId)) {
            throw new AccessDeniedException("club_access.denied");
        }
    }

    public List<Owner> accounts(Owner actor) {
        assertSuperAdmin(actor);
        return owners.findByRole(OwnerRole.CLUB_ADMIN);
    }

    public Owner account(Owner actor, Long id) {
        assertSuperAdmin(actor);
        Owner target = requireAccount(id);
        if (!target.isClubAdmin()) {
            throw new AccessDeniedException("club_access.denied");
        }
        return target;
    }

    @Transactional
    public void assign(Owner actor, Long id, Set<Long> clubIds) {
        Owner target = account(actor, id);
        Set<Long> selected = Set.copyOf(clubIds);
        Set<Long> existing = clubs.findAllById(selected).stream().map(Club::getId).collect(Collectors.toSet());
        if (!existing.equals(selected)) {
            throw new IllegalArgumentException("club_access.invalid_club");
        }
        target.setClubIds(new HashSet<>(selected));
        // Совместимость с прежней одиночной привязкой; пустой набор полностью снимает назначения.
        target.setClubId(selected.isEmpty() ? null : Collections.min(selected));
        owners.save(target);
    }

    private Owner requireAccount(Long id) {
        return owners.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private void assertSuperAdmin(Owner actor) {
        if (!actor.isSuperAdmin()) {
            throw new AccessDeniedException("club_access.denied");
        }
    }
}
