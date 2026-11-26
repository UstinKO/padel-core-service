package com.padle.core.padelcoreservice.validation;

import com.padle.core.padelcoreservice.dto.TournamentDto;
import com.padle.core.padelcoreservice.service.contact.OrganizerContacts;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class OrganizerContactValidator implements ConstraintValidator<ValidOrganizerContact, TournamentDto> {
    @Override public boolean isValid(TournamentDto value, ConstraintValidatorContext context) {
        if (value == null) return true;
        var contact = OrganizerContacts.input(value);
        if (contact.valid()) return true;
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate("{" + contact.errorCode() + "}")
                .addPropertyNode(value.getOrganizerPhone() == null ? "contactoOrganizador" : "organizerPhone")
                .addConstraintViolation();
        return false;
    }
}
