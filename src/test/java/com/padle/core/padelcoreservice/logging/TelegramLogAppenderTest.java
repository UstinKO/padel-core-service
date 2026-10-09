package com.padle.core.padelcoreservice.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;

// LFPT-0518: TelegramLogAppender — сообщения от StructuredAlert уходят в очередь как есть,
// остальные (не мигрированные места логирования) — в прежнем формате "🔴 <class> + текст"
// (критерий приёмки: регрессии для немигрированных мест быть не должно).
class TelegramLogAppenderTest {

    private final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    private final TelegramLogAppender appender = new TelegramLogAppender();
    private BlockingQueue<String> queue;

    @BeforeEach
    void setUp() {
        appender.setContext(context);
        queue = new LinkedBlockingQueue<>(10);
        context.putObject("TELEGRAM_QUEUE", queue);
    }

    @AfterEach
    void tearDown() {
        // setUp каждого теста кладёт свою свежую очередь — отдельная очистка не нужна
        // (ContextBase.putObject не принимает null, ConcurrentHashMap).
        MDC.clear();
    }

    private LoggingEvent event(String message, Map<String, String> mdc) {
        LoggingEvent event = new LoggingEvent();
        event.setLoggerName("com.padle.core.padelcoreservice.service.SomeService");
        event.setLevel(Level.WARN);
        event.setMessage(message);
        event.setMDCPropertyMap(mdc);
        return event;
    }

    @Test
    void append_structuredMessage_goesToQueueAsIs() {
        String structured = "🟠 Ошибка восстановления пароля\nEvent: PASSWORD_RESET_TOKEN_INVALID\nToken status: expired";
        Map<String, String> mdc = new HashMap<>();
        mdc.put(StructuredAlert.STRUCTURED_MDC_KEY, "true");

        appender.append(event(structured, mdc));

        assertThat(queue).containsExactly(structured);
    }

    @Test
    void append_plainMessage_fallsBackToOldWrappedFormat() {
        appender.append(event("Error de recaptcha inesperado", Map.of()));

        assertThat(queue).hasSize(1);
        String queued = queue.peek();
        assertThat(queued)
                .startsWith("🔴 <b>SomeService</b>")
                .contains("<code>Error de recaptcha inesperado</code>");
    }
}
