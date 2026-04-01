package com.padle.core.padelcoreservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Slf4j
@Service
public class MetricsService {

    private final MeterRegistry meterRegistry;
    private final Map<String, Timer> timerCache = new ConcurrentHashMap<>();
    private final Map<String, Counter> counterCache = new ConcurrentHashMap<>();

    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Создает или возвращает из кэша таймер
     */
    public Timer getTimer(String name, String description, String... tags) {
        String key = name + ":" + String.join(",", tags);
        return timerCache.computeIfAbsent(key, k -> {
            Timer.Builder builder = Timer.builder(name)
                    .description(description);

            if (tags != null && tags.length > 0) {
                // ИСПРАВЛЕНО: проверяем, что количество тегов четное
                if (tags.length % 2 != 0) {
                    log.warn("Tags array has odd length: {}, skipping tags for timer: {}", tags.length, name);
                } else {
                    builder.tags(tags);
                }
            }

            return builder.register(meterRegistry);
        });
    }

    /**
     * Создает или возвращает из кэша счетчик
     */
    public Counter getCounter(String name, String description, String... tags) {
        String key = name + ":" + String.join(",", tags);
        return counterCache.computeIfAbsent(key, k -> {
            Counter.Builder builder = Counter.builder(name)
                    .description(description);

            if (tags != null && tags.length > 0) {
                // ИСПРАВЛЕНО: проверяем, что количество тегов четное
                if (tags.length % 2 != 0) {
                    log.warn("Tags array has odd length: {}, skipping tags for counter: {}", tags.length, name);
                } else {
                    builder.tags(tags);
                }
            }

            return builder.register(meterRegistry);
        });
    }

    /**
     * Запускает таймер для метода
     */
    public Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * Останавливает таймер и регистрирует результат
     */
    public void stopTimer(Sample sample, String name, String description, String... tags) {
        if (sample != null) {
            sample.stop(getTimer(name, description, tags));
        }
    }

    /**
     * Инкрементирует счетчик
     */
    public void incrementCounter(String name, String description, String... tags) {
        getCounter(name, description, tags).increment();
    }

    /**
     * Инкрементирует счетчик на определенное значение
     */
    public void incrementCounter(String name, double amount, String description, String... tags) {
        getCounter(name, description, tags).increment(amount);
    }

    /**
     * Измеряет время выполнения и возвращает результат
     */
    public <T> T recordExecution(Supplier<T> supplier, String name, String description, String... tags) {
        Sample sample = startTimer();
        try {
            T result = supplier.get();
            return result;
        } finally {
            stopTimer(sample, name, description, tags);
        }
    }

    /**
     * Измеряет время выполнения с возможностью обработки ошибок
     */
    public <T> T recordExecutionWithErrors(Supplier<T> supplier, String name, String description,
                                           String successTag, String errorTag, String... baseTags) {
        Sample sample = startTimer();
        try {
            T result = supplier.get();
            // Успешное выполнение
            String[] tagsWithStatus = combineTags(baseTags, successTag);
            stopTimer(sample, name, description, tagsWithStatus);
            return result;
        } catch (Exception e) {
            // Ошибка выполнения
            String[] tagsWithStatus = combineTags(baseTags, errorTag);
            stopTimer(sample, name, description, tagsWithStatus);
            throw e;
        }
    }

    /**
     * Комбинирует базовые теги с новым тегом
     */
    private String[] combineTags(String[] baseTags, String newTag) {
        if (baseTags == null || baseTags.length == 0) {
            // newTag должен быть в формате "key=value"
            return new String[]{newTag.split("=")[0], newTag.split("=")[1]};
        }

        String[] result = new String[baseTags.length + 2];
        System.arraycopy(baseTags, 0, result, 0, baseTags.length);
        String[] parts = newTag.split("=");
        result[baseTags.length] = parts[0];
        result[baseTags.length + 1] = parts[1];
        return result;
    }
}