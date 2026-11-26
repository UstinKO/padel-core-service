package com.padle.core.padelcoreservice.controller.api;

import com.padle.core.padelcoreservice.model.Owner;
import com.padle.core.padelcoreservice.service.TournamentAccessService;
import com.padle.core.padelcoreservice.service.contact.OrganizerContacts;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/api/organizer-contact")
@PreAuthorize("hasAnyRole('OWNER','SUPER_ADMIN','ORGANIZER','ADMIN','CLUB_ADMIN')")
@RequiredArgsConstructor
public class OrganizerContactController {
    private final TournamentAccessService access;
    private final MessageSource messages;

    public record Input(Long eventId, String name, String phone, String country) {}
    public record Preview(boolean valid, OrganizerContacts.Contact contact, String message) {}

    @GetMapping("/countries")
    public List<OrganizerContacts.Country> countries(Locale locale) { return OrganizerContacts.countries(locale); }

    @PostMapping("/preview")
    public ResponseEntity<Preview> preview(@RequestBody Input input, @AuthenticationPrincipal Owner owner, Locale locale) {
        if (input.eventId() != null) access.assertCanManageTournament(owner, input.eventId());
        var contact = OrganizerContacts.validate(input.name(), input.phone(), input.country());
        String message = contact.valid() ? "" : messages.getMessage(contact.errorCode(), null, locale);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Preview(contact.valid(), contact, message));
    }
}
