package com.padle.core.padelcoreservice.service.marketing;

import com.padle.core.padelcoreservice.repository.marketing.QrStatsRepository;
import com.padle.core.padelcoreservice.exception.InvalidQrPeriodException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QrStatsService {
    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private final QrStatsRepository repository;

    // Один атомарный SQL выполняется без внешней транзакции: ошибка счётчика
    // не должна откатывать или блокировать пользовательское перенаправление.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void recordClick(String destination) {
        try {
            repository.increment(businessDate(Instant.now()), destination);
        } catch (DataAccessException ex) {
            log.error("Не удалось сохранить QR-переход Reby: {}", destination, ex);
        }
    }

    static LocalDate businessDate(Instant instant) {
        return LocalDate.ofInstant(instant, ZONE);
    }

    public QrStats stats(LocalDate from, LocalDate to) {
        LocalDate today = businessDate(Instant.now());
        LocalDate start = Optional.ofNullable(from).orElse(today.withDayOfMonth(1));
        LocalDate end = Optional.ofNullable(to).orElse(today.withDayOfMonth(today.lengthOfMonth()));
        if (end.isBefore(start) || ChronoUnit.DAYS.between(start, end) >= 366) {
            throw new InvalidQrPeriodException();
        }
        var days = repository.findDays(start, end);
        return new QrStats("reby", start, end,
                days.stream().mapToLong(QrStats.Day::site).sum(),
                days.stream().mapToLong(QrStats.Day::telegram).sum(), days);
    }
}
