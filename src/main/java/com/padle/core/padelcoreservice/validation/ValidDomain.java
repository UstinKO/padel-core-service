package com.padle.core.padelcoreservice.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DomainValidator.class)
@Documented
public @interface ValidDomain {
    String message() default "Dominio de email inválido";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}