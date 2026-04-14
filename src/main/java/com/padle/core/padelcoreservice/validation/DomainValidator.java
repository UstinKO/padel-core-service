package com.padle.core.padelcoreservice.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;
import java.util.regex.Pattern;

public class DomainValidator implements ConstraintValidator<ValidDomain, String> {

    private static final Pattern BOT_PATTERN = Pattern.compile(
            "(?i).*(support|admin|info|service|help|contact|no-reply)@(telus|test|example|domain)\\.(com|org|net)"
    );

    private static final Pattern RANDOM_STRING_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9]{10,}@.*$"
    );

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        if (email == null || email.isBlank()) {
            return true;
        }

        // 1. Проверка на бот-паттерны
        if (BOT_PATTERN.matcher(email).matches()) {
            return false;
        }

        // 2. Проверка на случайную строку перед @
        if (RANDOM_STRING_PATTERN.matcher(email).matches()) {
            return false;
        }

        return true;
    }

    // Правильная реализация проверки MX записи
    private boolean hasValidMXRecord(String domain) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("java.naming.provider.url", "dns://");

            InitialDirContext dnsContext = new InitialDirContext(env);
            Attributes attrs = dnsContext.getAttributes(domain, new String[]{"MX"});
            Attribute mxAttr = attrs.get("MX");

            return mxAttr != null && mxAttr.size() > 0;
        } catch (Exception e) {
            // Если не можем проверить DNS, пропускаем (лучше false срабатывания, чем блокировка легитимных пользователей)
            return true;
        }
    }
}