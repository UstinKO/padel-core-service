package com.padle.core.padelcoreservice.util;

import org.springframework.context.MessageSource;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Локализует открытый уровень в сохранённом названии, не меняя внутреннюю категорию. */
public final class TournamentLevelText {
    private static final Pattern ALL_LEVELS = Pattern.compile("(?<![\\p{L}\\p{N}_])TODOS(?![\\p{L}\\p{N}_])");

    private TournamentLevelText() {
    }

    public static String eventName(String name, MessageSource messages, Locale locale) {
        if (name == null || !ALL_LEVELS.matcher(name).find()) {
            return name;
        }
        String label = messages.getMessage("calendar.level.TODOS", null, locale);
        return ALL_LEVELS.matcher(name).replaceAll(Matcher.quoteReplacement(label));
    }
}
