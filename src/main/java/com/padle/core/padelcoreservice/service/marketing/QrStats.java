package com.padle.core.padelcoreservice.service.marketing;

import java.time.LocalDate;
import java.util.List;

public record QrStats(String source, LocalDate from, LocalDate to, long site, long telegram,
                      List<Day> days) {
    public record Day(LocalDate date, long site, long telegram) {}
}
