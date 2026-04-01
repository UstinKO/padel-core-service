package com.padle.core.padelcoreservice.aspect;

import com.padle.core.padelcoreservice.annotation.Timed;
import com.padle.core.padelcoreservice.metrics.MetricsService;
import io.micrometer.core.instrument.Timer.Sample;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class TimedAspect {

    private final MetricsService metricsService;

    @Around("@annotation(timed)")
    public Object measure(ProceedingJoinPoint joinPoint, Timed timed) throws Throwable {
        String metricName = resolveName(joinPoint, timed.name());
        String description = timed.description();
        String[] tags = resolveTags(joinPoint, timed.tags());

        Sample sample = metricsService.startTimer();

        try {
            Object result = joinPoint.proceed();

            // ИСПРАВЛЕНО: проверяем корректность тегов
            if (tags != null && tags.length > 0 && tags.length % 2 == 0) {
                metricsService.stopTimer(sample, metricName, description, tags);
            } else {
                metricsService.stopTimer(sample, metricName, description);
            }

            log.debug("Timed method: {} - success", metricName);
            return result;
        } catch (Exception e) {
            // Добавляем статус ошибки в теги
            String[] errorTags = combineTags(tags, "error", e.getClass().getSimpleName());
            metricsService.stopTimer(sample, metricName, description, errorTags);
            log.debug("Timed method: {} - failed with {}", metricName, e.getClass().getSimpleName());
            throw e;
        }
    }

    private String resolveName(ProceedingJoinPoint joinPoint, String customName) {
        if (!customName.isEmpty()) {
            return customName;
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getMethod().getName();
    }

    private String[] resolveTags(ProceedingJoinPoint joinPoint, String[] customTags) {
        if (customTags == null || customTags.length == 0) {
            return new String[0];
        }

        List<String> tags = new ArrayList<>();

        for (String tag : customTags) {
            if (tag.contains("=")) {
                String[] parts = tag.split("=");
                tags.add(parts[0]);
                tags.add(parts[1]);
            } else {
                log.warn("Invalid tag format: '{}', expected 'key=value'", tag);
            }
        }

        return tags.toArray(new String[0]);
    }

    private String[] combineTags(String[] existingTags, String key, String value) {
        if (existingTags == null || existingTags.length == 0) {
            return new String[]{key, value};
        }

        String[] result = new String[existingTags.length + 2];
        System.arraycopy(existingTags, 0, result, 0, existingTags.length);
        result[existingTags.length] = key;
        result[existingTags.length + 1] = value;
        return result;
    }
}