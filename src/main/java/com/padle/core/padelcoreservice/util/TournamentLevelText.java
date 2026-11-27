package com.padle.core.padelcoreservice.util;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Локализует открытый уровень в сохранённом названии, не меняя внутреннюю категорию. */
@Component("eventLevelText")
public final class TournamentLevelText {
    private static final Pattern ALL_LEVELS = Pattern.compile("(?<![\\p{L}\\p{N}_])TODOS(?![\\p{L}\\p{N}_])");

    private final MessageSource messages;

    public TournamentLevelText(MessageSource messages) {
        this.messages = messages;
    }

    public String eventName(String name, Locale locale) {
        return eventName(name, messages, locale);
    }

    public static String eventName(String name, MessageSource messages, Locale locale) {
        if (name == null || !ALL_LEVELS.matcher(name).find()) {
            return name;
        }
        String label = messages.getMessage("calendar.level.TODOS", null, locale);
        return ALL_LEVELS.matcher(name).replaceAll(Matcher.quoteReplacement(label));
    }
}
