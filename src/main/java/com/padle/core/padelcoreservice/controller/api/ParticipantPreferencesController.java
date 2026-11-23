package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.dto.PlayingSideDtos;
import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.ParticipantPreferences;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ParticipantPreferencesController {
    private final ParticipantPreferences service;

    @GetMapping("/api/admin/events/{id}/participant-preferences")
    @PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN','ADMIN','ORGANIZER','CLUB_ADMIN')")
    public ResponseEntity<List<PlayingSideDtos.Participant>> roster(@PathVariable long id, @AuthenticationPrincipal Owner owner) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.roster(owner, id));
    }
}
