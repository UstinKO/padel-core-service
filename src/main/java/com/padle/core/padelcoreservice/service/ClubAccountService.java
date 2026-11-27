package com.padle.core.padelcoreservice.service;

import com.padle.core.application.identity.OwnerAccount;
import com.padle.core.application.identity.OwnerAccounts;
import com.padle.core.application.clubs.ClubAdminReadPort;
import com.padle.core.padelcoreservice.dto.ClubDto;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.mapper.ClubDtoMapper;
import com.padle.core.padelcoreservice.model.Owner;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubAccountService {
    private final OwnerAccounts owners;
    private final ClubAdminReadPort clubReads;
    private final ClubDtoMapper mapper;

    public Set<Long> assignedClubIds(Owner account) {
        // Сессия может быть открыта до изменения назначений: читаем актуальные права из базы.
        return requireAccount(account.getId()).assignedClubIds();
    }

    public List<ClubDto> availableClubs(Owner account) {
        if (!account.isClubAdmin()) {
            return clubReads.findActiveOrderedByName().stream().map(mapper::toDto).toList();
        }
        Set<Long> assigned = assignedClubIds(account);
        return clubReads.findActiveOrderedByName().stream()
                .filter(club -> assigned.contains(club.id())).map(mapper::toDto).toList();
    }

    public void assertCanCreateAt(Owner account, Long clubId) {
        if (account.isClubAdmin()) {
            assertClubAssignment(account.getId(), clubId);
        }
    }

    public void assertCanCreateAt(OwnerAccount account, Long clubId) {
        if (account.isClubAdmin()) {
            assertClubAssignment(account.id(), clubId);
        }
    }

    private void assertClubAssignment(Long ownerId, Long clubId) {
        if (clubId == null || !requireAccount(ownerId).assignedClubIds().contains(clubId)
                || !clubReads.existsActiveById(clubId)) {
            throw new AccessDeniedException("club_access.denied");
        }
    }

    public void assertCanViewClub(Owner account, Long clubId) {
        if (account.isClubAdmin() && !assignedClubIds(account).contains(clubId)) {
            throw new AccessDeniedException("club_access.denied");
        }
    }

    public List<OwnerAccount> accounts(Owner actor) {
        assertSuperAdmin(actor);
        return owners.findByRole(OwnerAccount.Role.CLUB_ADMIN);
    }

    public OwnerAccount account(Owner actor, Long id) {
        assertSuperAdmin(actor);
        OwnerAccount target = requireAccount(id);
        if (!target.isClubAdmin()) {
            throw new AccessDeniedException("club_access.denied");
        }
        return target;
    }

    @Transactional
    public void assign(Owner actor, Long id, Set<Long> clubIds) {
        account(actor, id);
        Set<Long> selected = Set.copyOf(clubIds);
        Set<Long> existing = clubReads.findExistingIds(selected);
        if (!existing.equals(selected)) {
            throw new IllegalArgumentException("club_access.invalid_club");
        }
        // Совместимость с прежней одиночной привязкой; пустой набор полностью снимает назначения.
        owners.replaceClubAssignments(id, selected.isEmpty() ? null : Collections.min(selected), selected);
    }

    private OwnerAccount requireAccount(Long id) {
        return owners.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private void assertSuperAdmin(Owner actor) {
        if (!actor.isSuperAdmin()) {
            throw new AccessDeniedException("club_access.denied");
        }
    }
}
