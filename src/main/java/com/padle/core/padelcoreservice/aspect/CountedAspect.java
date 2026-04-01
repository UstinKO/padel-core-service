package com.padle.core.padelcoreservice.aspect;

import com.padle.core.padelcoreservice.annotation.Counted;
import com.padle.core.padelcoreservice.metrics.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class CountedAspect {

    private final MetricsService metricsService;

    @Before("@annotation(counted)")
    public void count(JoinPoint joinPoint, Counted counted) {
        String metricName = resolveName(joinPoint, counted.name());
        String description = counted.description();
        String[] tags = resolveTags(joinPoint, counted.tags());

        // ИСПРАВЛЕНО: проверяем, что теги корректны перед передачей
        if (tags != null && tags.length > 0 && tags.length % 2 == 0) {
            metricsService.incrementCounter(metricName, description, tags);
            log.debug("Incremented counter: {} with tags: {}", metricName, Arrays.toString(tags));
        } else if (tags == null || tags.length == 0) {
            metricsService.incrementCounter(metricName, description);
            log.debug("Incremented counter: {} without tags", metricName);
        } else {
            log.warn("Invalid tags for counter {}: tags array has odd length {}", metricName, tags.length);
            // Все равно инкрементируем без тегов
            metricsService.incrementCounter(metricName, description);
        }
    }

    @AfterReturning(pointcut = "@annotation(counted)", returning = "result")
    public void countWithResult(JoinPoint joinPoint, Counted counted, Object result) {
        // Можно добавить логику для подсчета с учетом результата
        if (result != null && counted.tags().length == 0) {
            String metricName = resolveName(joinPoint, counted.name()) + ".by.result";
            String[] tags = new String[]{"result", result.getClass().getSimpleName()};
            metricsService.incrementCounter(metricName, counted.description(), tags);
        }
    }

    private String resolveName(JoinPoint joinPoint, String customName) {
        if (!customName.isEmpty()) {
            return customName;
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getMethod().getName();
    }

    private String[] resolveTags(JoinPoint joinPoint, String[] customTags) {
        if (customTags == null || customTags.length == 0) {
            return new String[0];
        }

        List<String> tags = new ArrayList<>();

        for (String tag : customTags) {
            // Проверяем, что тег в формате key=value
            if (tag.contains("=")) {
                tags.add(tag.split("=")[0]);
                tags.add(tag.split("=")[1]);
            } else {
                log.warn("Invalid tag format: '{}', expected 'key=value'", tag);
            }
        }

        return tags.toArray(new String[0]);
    }
}