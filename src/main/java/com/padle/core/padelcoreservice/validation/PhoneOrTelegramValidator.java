package com.padle.core.padelcoreservice.validation;

import com.padle.core.padelcoreservice.dto.RegistroRequestDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PhoneOrTelegramValidator implements ConstraintValidator<RequiresPhoneOrTelegram, RegistroRequestDto> {

    @Override
    public boolean isValid(RegistroRequestDto dto, ConstraintValidatorContext context) {
        boolean hasPhone = dto.getTelefono() != null && !dto.getTelefono().isBlank();
        boolean hasTelegram = dto.getTelegramUsername() != null && !dto.getTelegramUsername().isBlank();

        if (hasPhone || hasTelegram) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("telefono")
                .addConstraintViolation();
        return false;
    }
}
