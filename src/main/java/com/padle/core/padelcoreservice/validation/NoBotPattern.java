package com.padle.core.padelcoreservice.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BotPatternValidator.class)
@Documented
public @interface NoBotPattern {
    String message() default "Patrón sospechoso detectado";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}