package com.padle.core.padelcoreservice.aspect;

import com.padle.core.padelcoreservice.annotation.TrackErrors;
import com.padle.core.padelcoreservice.metrics.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
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
public class TrackErrorsAspect {

    private final MetricsService metricsService;

    @AfterThrowing(pointcut = "@annotation(trackErrors)", throwing = "exception")
    public void trackError(JoinPoint joinPoint, TrackErrors trackErrors, Exception exception) {
        String metricName = resolveName(joinPoint, trackErrors.name());
        Class<?>[] trackedExceptions = trackErrors.exceptions();

        // Проверяем, нужно ли отслеживать это исключение
        boolean shouldTrack = trackedExceptions.length == 0;
        if (!shouldTrack) {
            for (Class<?> trackedException : trackedExceptions) {
                if (trackedException.isAssignableFrom(exception.getClass())) {
                    shouldTrack = true;
                    break;
                }
            }
        }

        if (shouldTrack) {
            String[] tags = resolveTags(joinPoint, trackErrors.tags(), exception);
            metricsService.incrementCounter(metricName, "Error counter", tags);
            log.debug("Tracked error: {} - {} with tags: {}",
                    exception.getClass().getSimpleName(),
                    exception.getMessage(),
                    Arrays.toString(tags));
        }
    }

    private String resolveName(JoinPoint joinPoint, String customName) {
        if (!customName.isEmpty()) {
            return customName;
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getMethod().getName() + ".errors";
    }

    // #294: MetricsService.getCounter ждёт плоский чередующийся массив [key1, value1, key2, ...],
    // а customTags (как и везде в проекте, см. @Counted(tags = {"operation=cancel"})) приходят
    // строками вида "key=value" — раньше они копировались как есть, плюс тег exception добавлялся
    // ОДНИМ элементом "exception=ClassName", а не парой — итоговый массив гарантированно нечётной
    // длины при любом вызове, MetricsService молча отбрасывал все теги. Разбираем "key=value" так
    // же, как уже корректно делают CountedAspect/TimedAspect.resolveTags().
    private String[] resolveTags(JoinPoint joinPoint, String[] customTags, Exception exception) {
        List<String> tags = new ArrayList<>();
        for (String tag : customTags) {
            if (tag.contains("=")) {
                String[] parts = tag.split("=", 2);
                tags.add(parts[0]);
                tags.add(parts[1]);
            } else {
                log.warn("Invalid tag format: '{}', expected 'key=value'", tag);
            }
        }
        tags.add("exception");
        tags.add(exception.getClass().getSimpleName());
        return tags.toArray(new String[0]);
    }
}