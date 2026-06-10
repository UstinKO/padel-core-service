package com.padle.core.padelcoreservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "telegram")
public class TelegramProperties {
    /** Чат для admin-алертов (ошибки, атаки). */
    private String botToken;
    private String chatId;
    private Integer threadId;
    private boolean enabled = false;

}
