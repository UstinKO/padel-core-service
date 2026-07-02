package com.padle.core.padelcoreservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * @Async методы (сейчас — отправка email) не должны держать открытыми
 * HTTP-запрос/транзакцию на время SMTP-вызова.
 *
 * order = HIGHEST_PRECEDENCE: async-advisor должен оборачивать {@code @Timed}/{@code @TrackErrors}
 * (у обоих аспектов порядок не задан = LOWEST_PRECEDENCE), иначе они замеряют/ловят
 * исключения на вызывающем потоке до ухода в executor, а не реальное выполнение метода.
 */
@Slf4j
@Configuration
@EnableAsync(order = Ordered.HIGHEST_PRECEDENCE)
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-email-");
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> log.error(
                "Необработанная ошибка в @Async методе {}.{}: {}",
                method.getDeclaringClass().getSimpleName(), method.getName(), ex.getMessage(), ex);
    }
}
