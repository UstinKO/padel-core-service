package com.padle.core.padelcoreservice.service;

import com.padle.core.application.identity.OwnerAccount;
import com.padle.core.application.identity.OwnerAccounts;
import com.padle.core.padelcoreservice.annotation.Timed;
import com.padle.core.padelcoreservice.dto.TournamentDto;
import java.util.Objects;
import com.padle.core.padelcoreservice.dto.calendar.OrganizationNameRequest;
import com.padle.core.padelcoreservice.dto.calendar.OrganizationAccount;
import com.padle.core.padelcoreservice.dto.calendar.OrganizationSettings;
import com.padle.core.padelcoreservice.dto.calendar.OrganizerOrganization;
import com.padle.core.padelcoreservice.exception.ResourceNotFoundException;
import com.padle.core.padelcoreservice.mapper.AdminCalendarMapper;
import com.padle.core.padelcoreservice.model.Owner;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrganizerOrganizationService {
    private final OwnerAccounts owners;
    private final AdminCalendarMapper mapper;

    @Timed(name = "admin.organizations.settings", tags = {"type=calendar"})
    public OrganizationSettings settings(Owner actor) {
        List<OwnerAccount> available = actor.isSuperAdmin() ? owners.findAll() : List.of(account(actor.getId()));
        return new OrganizationSettings(actor.getId(), available.stream().map(mapper::toOrganizationAccount)
                .sorted(Comparator.comparing(OrganizationAccount::id)).toList());
    }

    @Timed(name = "admin.organizations.events", tags = {"type=calendar"})
    public List<OrganizerOrganization> forEvents(List<TournamentDto> visible) {
        return owners.findAllById(visible.stream().map(TournamentDto::getOwnerId)
                        .filter(Objects::nonNull).distinct().toList()).stream().map(mapper::toOrganizer)
                .sorted(Comparator.comparing(OrganizerOrganization::name,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(OrganizerOrganization::id)).toList();
    }

    @Transactional
    public OrganizerOrganization update(Owner actor, Long id, OrganizationNameRequest request) {
        if (!actor.isSuperAdmin() && !actor.getId().equals(id)) {
            throw new AccessDeniedException("ORGANIZATION_ACCESS_DENIED");
        }
        account(id);
        return mapper.toOrganizer(owners.updateOrganizationName(id, request.name()));
    }

    private OwnerAccount account(Long id) {
        return owners.findById(id).orElseThrow(() -> new ResourceNotFoundException("ORGANIZER_NOT_FOUND"));
    }
}
