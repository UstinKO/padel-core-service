package com.padle.core.padelcoreservice.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class PlayerTelegramMessages {
    private final MessageSource messages;

    public String text(String key, Locale locale, Object... args) {
        return messages.getMessage("telegram.player." + key, args, locale);
    }
}
