package com.padle.core.padelcoreservice.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NotDisposableEmailValidator.class)
public @interface NotDisposableEmail {
    String message() default "{account.EMAIL_DOMAIN_INVALID}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
