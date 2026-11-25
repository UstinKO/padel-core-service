package com.padle.core.padelcoreservice.service.calendar;

import com.padle.core.padelcoreservice.exception.PublicDiscoveryException;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;

public record PublicCalendarRange(YearMonth month, LocalDate from, LocalDate to) {
    public static PublicCalendarRange of(String value) {
        try {
            if (value != null && !value.matches("[0-9]{4}-[0-9]{2}")) {
                throw new PublicDiscoveryException("INVALID_CATALOG_FILTER");
            }
            YearMonth month = value == null ? YearMonth.now(EventSchedule.zone(null)) : YearMonth.parse(value);
            LocalDate from = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate to = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            if (from.getYear() < 1 || to.getYear() > 9999) {
                throw new PublicDiscoveryException("INVALID_CATALOG_FILTER");
            }
            return new PublicCalendarRange(month, from, to);
        } catch (DateTimeException exception) {
            throw new PublicDiscoveryException("INVALID_CATALOG_FILTER");
        }
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(from) && !date.isAfter(to);
    }
}
