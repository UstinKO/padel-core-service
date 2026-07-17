package com.padle.core.padelcoreservice.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PhoneOrTelegramValidator.class)
@Documented
public @interface RequiresPhoneOrTelegram {
    String message() default "Debes indicar un teléfono o un usuario de Telegram";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
