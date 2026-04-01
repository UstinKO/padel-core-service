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

import java.util.Arrays;

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

    private String[] resolveTags(JoinPoint joinPoint, String[] customTags, Exception exception) {
        String[] tags = Arrays.copyOf(customTags, customTags.length + 1);
        tags[customTags.length] = "exception=" + exception.getClass().getSimpleName();
        return tags;
    }
}