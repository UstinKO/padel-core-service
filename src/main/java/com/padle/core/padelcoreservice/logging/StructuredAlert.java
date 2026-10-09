package com.padle.core.padelcoreservice.logging;

import com.padle.core.padelcoreservice.security.RequestIdFilter;
import org.slf4j.MDC;
import org.slf4j.Logger;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * LFPT-0518: собирает структурированный Telegram-алерт — заголовок с эмодзи по уровню
 * серьёзности, Event, предметные поля в заданном порядке, Request ID, Time — вместо
 * прежнего "🔴 &lt;имя класса&gt; + текст сообщения".
 * <p>
 * {@link TelegramLogAppender} распознаёт такие сообщения по MDC-флагу {@link #STRUCTURED_MDC_KEY}
 * и отправляет готовый текст как есть, не оборачивая в старый формат — это сохраняет
 * старое поведение для всех остальных мест логирования, не мигрированных на этот механизм.
 */
public final class StructuredAlert {

    static final String STRUCTURED_MDC_KEY = "alertStructured";

    // Рынок проекта — Аргентина (CLAUDE.md), формат времени — как в примере заказчика (dd/MM/yyyy HH:mm).
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int MAX_FIELD_LENGTH = 300;

    private final StringBuilder body = new StringBuilder();

    private StructuredAlert(AlertSeverity severity, String title, String event) {
        body.append(severity.emoji()).append(' ').append(escapeHtml(title)).append('\n')
                .append("Event: ").append(escapeHtml(event));
    }

    public static StructuredAlert of(AlertSeverity severity, String title, String event) {
        return new StructuredAlert(severity, title, event);
    }

    /** Поле пропускается, если значение null — чтобы не засорять алерт лишними строками. */
    public StructuredAlert field(String name, Object value) {
        if (value != null) {
            body.append('\n').append(escapeHtml(name)).append(": ").append(escapeHtml(truncate(value.toString())));
        }
        return this;
    }

    public void warn(Logger log) {
        log(log, null, false);
    }

    public void error(Logger log) {
        log(log, null, true);
    }

    public void error(Logger log, Throwable cause) {
        log(log, cause, true);
    }

    private void log(Logger log, Throwable cause, boolean error) {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        body.append('\n').append("Request ID: ").append(requestId != null ? requestId : "—");
        body.append('\n').append("Time: ").append(ZonedDateTime.now(ZONE).format(TIME_FORMAT));
        String message = body.toString();

        MDC.put(STRUCTURED_MDC_KEY, "true");
        try {
            if (error) {
                if (cause != null) {
                    log.error(message, cause);
                } else {
                    log.error(message);
                }
            } else {
                log.warn(message);
            }
        } finally {
            MDC.remove(STRUCTURED_MDC_KEY);
        }
    }

    private static String truncate(String value) {
        return value.length() > MAX_FIELD_LENGTH ? value.substring(0, MAX_FIELD_LENGTH) + "…" : value;
    }

    // Сообщение уходит в Telegram с parse_mode=HTML (TelegramService) — без экранирования
    // "<"/"&" в значении поля (например, из текста исключения или query-параметров Endpoint)
    // сообщение может не отрендериться.
    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
