package com.padle.core.padelcoreservice.logging;

/**
 * Уровень серьёзности алерта в Telegram (LFPT-0518) — чтобы по одному значку
 * сразу было понятно, нужно ли реально разбираться с кодом (Application Error)
 * или это сработавшая защита (Security Warning) / ожидаемая ошибка пользователя
 * или бизнес-правила (User/Validation Error).
 */
public enum AlertSeverity {

    APPLICATION_ERROR("🔴", "Application Error"),
    SECURITY_WARNING("🟡", "Security Warning"),
    USER_VALIDATION_ERROR("🟠", "User/Validation Error");

    private final String emoji;
    private final String label;

    AlertSeverity(String emoji, String label) {
        this.emoji = emoji;
        this.label = label;
    }

    public String emoji() {
        return emoji;
    }

    public String label() {
        return label;
    }
}
