package com.padle.core.padelcoreservice.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Set;
import java.util.regex.Pattern;

public class DomainValidator implements ConstraintValidator<ValidDomain, String> {

    /**
     * Популярные легитимные домены — никогда не блокируем.
     * Gmail, Outlook, Yahoo, iCloud, Proton и другие массовые провайдеры.
     */
    private static final Set<String> TRUSTED_DOMAINS = Set.of(
            "gmail.com", "googlemail.com",
            "outlook.com", "hotmail.com", "live.com", "msn.com",
            "yahoo.com", "yahoo.es", "yahoo.com.ar",
            "icloud.com", "me.com", "mac.com",
            "proton.me", "protonmail.com",
            "mail.ru", "yandex.ru", "yandex.com",
            "aol.com",
            "zoho.com",
            "tutanota.com"
    );

    /**
     * Бот-паттерны — только очень специфичные комбинации служебных адресов
     * на явно тестовых/фейковых доменах.
     */
    private static final Pattern BOT_PATTERN = Pattern.compile(
            "(?i)^(support|admin|info|service|help|contact|no-reply|noreply)@(telus|test|example|domain|fake|spam)\\.(com|org|net)$"
    );

    /**
     * Случайная строка — только если домен НЕ в доверенных.
     * Паттерн: 15+ символов без цифр, точек, подчёркиваний — явный рандом.
     * НЕ блокируем johnsmith12@gmail.com (12 символов — норма).
     */
    private static final Pattern SUSPICIOUS_LOCAL = Pattern.compile(
            "^[a-zA-Z0-9]{20,}$" // 20+ символов подряд без разделителей — подозрительно
    );

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null || email.isBlank()) {
            return true;
        }

        String lower = email.toLowerCase().trim();

        // Извлекаем домен
        int atIdx = lower.lastIndexOf('@');
        if (atIdx < 0) {
            return false;
        }
        String domain = lower.substring(atIdx + 1);
        String localPart = lower.substring(0, atIdx);

        // Доверенные домены — пропускаем все проверки
        if (TRUSTED_DOMAINS.contains(domain)) {
            return true;
        }

        // Бот-паттерны только для ненадёжных доменов
        if (BOT_PATTERN.matcher(lower).matches()) {
            return false;
        }

        // Подозрительно длинная случайная локальная часть — только для неизвестных доменов
        if (SUSPICIOUS_LOCAL.matcher(localPart).matches()) {
            return false;
        }

        return true;
    }
}