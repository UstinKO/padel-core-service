package com.padle.core.padelcoreservice.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class BotPatternValidator implements ConstraintValidator<NoBotPattern, String> {

    private static final Pattern RANDOM_STRING_PATTERN = Pattern.compile(
            "^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9]).{10,}$"  // Случайная строка из букв разного регистра и цифр
    );

    private static final Pattern REPETITIVE_PATTERN = Pattern.compile(
            "(.)\\1{5,}"  // Повторяющиеся символы (например, "aaaaaa")
    );

    private static final Pattern ONLY_CONSONANTS = Pattern.compile(
            "^[bcdfghjklmnpqrstvwxyzBCDFGHJKLMNPQRSTVWXYZ]{8,}$"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }

        // Проверка на случайную строку (как в ваших логах: MFclENHzfgO)
        if (RANDOM_STRING_PATTERN.matcher(value).matches()) {
            return false;
        }

        // Проверка на повторяющиеся символы
        if (REPETITIVE_PATTERN.matcher(value).find()) {
            return false;
        }

        // Проверка, что строка не состоит только из согласных
        if (ONLY_CONSONANTS.matcher(value).matches()) {
            return false;
        }

        return true;
    }
}