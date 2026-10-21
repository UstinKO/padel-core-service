package com.padle.core.padelcoreservice.dto;

// LFPT-0677: минимальный набор идентифицирующих полей игрока для отчёта об аудите дублей.
public record PlayerSummaryDto(
        Long id,
        String nombre,
        String apellido,
        String email,
        String telefono,
        String telegramUsername,
        boolean guestAccount,
        boolean oauth2User
) {
}
