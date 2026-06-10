package com.padle.core.padelcoreservice.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.util.concurrent.BlockingQueue;

/**
 * Appender пишет в очередь, которую передаёт Spring после старта через LoggerContext
 * (ключ TELEGRAM_QUEUE). Это обходит проблему раздельных static-полей при DevTools
 * restart class loader: LoggerContext общий для всех class loader'ов.
 */
public class TelegramLogAppender extends AppenderBase<ILoggingEvent> {

    @Override
    @SuppressWarnings("unchecked")
    protected void append(ILoggingEvent event) {
        BlockingQueue<String> queue = (BlockingQueue<String>)
                ((LoggerContext) context).getObject("TELEGRAM_QUEUE");
        if (queue == null) return; // Spring ещё не поднялся

        String logger = event.getLoggerName();
        String shortLogger = logger.contains(".")
                ? logger.substring(logger.lastIndexOf('.') + 1)
                : logger;

        String message = event.getFormattedMessage();
        if (message == null) message = "";
        if (message.length() > 400) message = message.substring(0, 400) + "…";

        queue.offer(String.format("🔴 <b>%s</b>\n<code>%s</code>", shortLogger, message));
    }
}
