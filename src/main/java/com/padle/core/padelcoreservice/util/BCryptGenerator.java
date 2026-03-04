package com.padle.core.padelcoreservice.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BCryptGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
        String password = "admin123";
        String hash = encoder.encode(password);
        System.out.println("Password: " + password);
        System.out.println("BCrypt hash: " + hash);
        System.out.println("\nИспользуйте этот хеш в Liquibase скрипте:");
        System.out.println(hash);
    }
}