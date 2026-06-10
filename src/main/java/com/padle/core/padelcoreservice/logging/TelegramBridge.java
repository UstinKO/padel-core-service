package com.padle.core.padelcoreservice.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.Appender;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;

/**
 * Передаёт очередь алертов из Spring-контекста в TelegramLogAppender через LoggerContext.
 * LoggerContext — синглтон в parent class loader, виден всем class loader'ам одновременно.
 * Это решает проблему DevTools restart class loader, при которой static QUEUE в Logback
 * и static QUEUE в Spring оказываются разными объектами.
 */
@Slf4j
@Component
public class TelegramBridge {

    @PostConstruct
    public void init() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.putObject("TELEGRAM_QUEUE", TelegramAlertQueue.QUEUE);

        // Проверяем что TELEGRAM appender действительно в цепочке
        Logger appLogger = loggerContext.getLogger("com.padle.core");
        Iterator<Appender<ch.qos.logback.classic.spi.ILoggingEvent>> it =
                appLogger.iteratorForAppenders();
        boolean found = false;
        while (it.hasNext()) {
            String name = it.next().getName();
            if ("TELEGRAM".equals(name)) found = true;
        }
        if (found) {
            log.info("TelegramBridge: очередь алертов привязана, TELEGRAM appender активен");
        } else {
            log.warn("TelegramBridge: TELEGRAM appender НЕ найден в логгере com.padle.core — ошибки не будут приходить в Telegram");
        }
    }
}
