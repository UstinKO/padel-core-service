package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.calendar.OrganizationNameRequest;
import com.padle.core.padelcoreservice.dto.calendar.OrganizationSettings;
import com.padle.core.padelcoreservice.dto.calendar.OrganizerOrganization;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.OrganizerOrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/organizers")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'OWNER', 'ORGANIZER', 'CLUB_ADMIN')")
@RequiredArgsConstructor
public class AdminOrganizationController {
    private final OrganizerOrganizationService organizations;

    @GetMapping("/organizations")
    public ResponseEntity<OrganizationSettings> settings(@AuthenticationPrincipal Owner actor) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(organizations.settings(actor));
    }

    @PutMapping("/{id}/organization")
    public ResponseEntity<OrganizerOrganization> update(@AuthenticationPrincipal Owner actor, @PathVariable Long id,
                                                       @Valid @RequestBody OrganizationNameRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(organizations.update(actor, id, request));
    }
}
