package com.padle.core.padelcoreservice.telegram;

import com.padle.core.padelcoreservice.util.TournamentLevelText;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class PlayerTelegramMessages {
    private final MessageSource messages;

    public String eventName(String name, Locale locale) {
        return TournamentLevelText.eventName(name, messages, locale);
    }

    public String text(String key, Locale locale, Object... args) {
        return messages.getMessage("telegram.player." + key, args, locale);
    }
}
