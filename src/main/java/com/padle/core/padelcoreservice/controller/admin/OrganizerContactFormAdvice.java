package com.padle.core.padelcoreservice.controller.admin;

import com.padle.core.padelcoreservice.service.contact.OrganizerContacts;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(assignableTypes = {AdminController.class, AdminTournamentCopyController.class})
public class OrganizerContactFormAdvice {
    @ModelAttribute("organizerCountries")
    public List<OrganizerContacts.Country> countries(Locale locale) {
        return OrganizerContacts.countries(locale);
    }
}
