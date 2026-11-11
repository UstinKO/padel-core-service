package com.padle.core.padelcoreservice.validation;

import dev.caceresenzo.disposableemaildomains.DisposableEmailDomains;
import lombok.RequiredArgsConstructor;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

@RequiredArgsConstructor
public class NotDisposableEmailValidator implements ConstraintValidator<NotDisposableEmail, String> {
    private final DisposableEmailDomains domains;
    @Override public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || !domains.testEmail(value);
    }
}
