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

    private static final Pattern ONLY_VOWELS = Pattern.compile(
            "^[aeiouyAEIOUY]{8,}$"  // Только гласные — подозрительно
    );

    private static final Pattern LETTER_DIGIT_MIX = Pattern.compile(
            "(?=.*[a-z])(?=.*[0-9])(?=.*[A-Z])"  // Смесь регистров + цифры
    );

    private static final Pattern THREE_CONSECUTIVE_CONSONANTS = Pattern.compile(
            "[bcdfghjklmnpqrstvwxyzBCDFGHJKLMNPQRSTVWXYZ]{4,}"  // 4+ согласных подряд
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }

        String trimmed = value.trim();

        // Случайная строка типа sdf.234s
        if (LETTER_DIGIT_MIX.matcher(trimmed).find()
                && trimmed.length() < 6
                && trimmed.contains(".")) {
            return false;
        }

        // Только гласные — baaaad
        if (ONLY_VOWELS.matcher(trimmed).matches()) {
            return false;
        }

        // 4+ согласных подряд без гласных
        if (THREE_CONSECUTIVE_CONSONANTS.matcher(trimmed).find()
                && !trimmed.toLowerCase().matches(".*[aeiouyáéíóúü].*")) {
            return false;
        }

        // Существующие проверки...
        if (RANDOM_STRING_PATTERN.matcher(trimmed).matches()) {
            return false;
        }

        if (REPETITIVE_PATTERN.matcher(trimmed).find()) {
            return false;
        }

        if (ONLY_CONSONANTS.matcher(trimmed).matches()) {
            return false;
        }

        return true;
    }
}