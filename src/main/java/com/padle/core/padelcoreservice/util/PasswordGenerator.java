package com.padle.core.padelcoreservice.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String rawPassword = "Admin123!";
        String encodedPassword = encoder.encode(rawPassword);
        System.out.println("Пароль: " + rawPassword);
        System.out.println("Хеш:   " + encodedPassword);

        // Проверка
        boolean matches = encoder.matches(rawPassword, encodedPassword);
        System.out.println("Проверка: " + (matches ? "✓ OK" : "✗ Ошибка"));
    }
}