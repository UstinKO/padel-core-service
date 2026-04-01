package com.padle.core.padelcoreservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class EmailMetricsService {

    private final MeterRegistry meterRegistry;

    private final Map<String, Counter> sendCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> attemptCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> errorCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> rejectedCounters = new ConcurrentHashMap<>();

    private final AtomicInteger dailyCounter = new AtomicInteger(0);
    private LocalDate currentDate = LocalDate.now();

    @Getter
    @Value("${app.email.daily-limit:300}")
    private int dailyLimit;

    public EmailMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // Простой способ регистрации Gauge
        meterRegistry.gauge("email.daily.total", dailyCounter, AtomicInteger::get);
        meterRegistry.gauge("email.daily.limit", this, service -> service.dailyLimit);
    }

    public void recordEmailAttempt(String emailType) {
        resetDailyCounterIfNeeded();
        dailyCounter.incrementAndGet();

        attemptCounters.computeIfAbsent(emailType, type ->
                Counter.builder("email.send.attempts")
                        .tag("type", type)
                        .register(meterRegistry)
        ).increment();

        log.debug("Email attempt recorded for type: {}", emailType);
    }

    public void recordEmailSent(String emailType) {
        sendCounters.computeIfAbsent(emailType, type ->
                Counter.builder("email.sent.total")
                        .tag("type", type)
                        .register(meterRegistry)
        ).increment();

        log.debug("Email sent recorded for type: {}", emailType);
    }

    public void recordEmailError(String emailType, String errorType) {
        errorCounters.computeIfAbsent(emailType + "_" + errorType, key ->
                Counter.builder("email.errors.total")
                        .tag("type", emailType)
                        .tag("error", errorType)
                        .register(meterRegistry)
        ).increment();

        log.warn("Email error recorded for type: {}, error: {}", emailType, errorType);
    }

    public void recordEmailRejected(String emailType, String reason) {
        rejectedCounters.computeIfAbsent(emailType + "_" + reason, key ->
                Counter.builder("email.rejected.total")
                        .tag("type", emailType)
                        .tag("reason", reason)
                        .register(meterRegistry)
        ).increment();

        log.warn("Email rejected for type: {}, reason: {}", emailType, reason);
    }

    public boolean isDailyLimitReached(int limit) {
        resetDailyCounterIfNeeded();
        return dailyCounter.get() >= limit;
    }

    public int getDailyEmailCount() {
        resetDailyCounterIfNeeded();
        return dailyCounter.get();
    }

    private void resetDailyCounterIfNeeded() {
        LocalDate today = LocalDate.now();
        if (!today.equals(currentDate)) {
            log.info("Resetting daily email counter from {} to 0", dailyCounter.get());
            dailyCounter.set(0);
            currentDate = today;
        }
    }

    public Map<String, Object> getEmailStats() {
        Map<String, Object> stats = new ConcurrentHashMap<>();
        stats.put("dailyCount", dailyCounter.get());
        stats.put("dailyLimit", dailyLimit);
        stats.put("remainingQuota", Math.max(0, dailyLimit - dailyCounter.get()));
        stats.put("usagePercentage", (dailyCounter.get() * 100.0) / dailyLimit);
        return stats;
    }
}