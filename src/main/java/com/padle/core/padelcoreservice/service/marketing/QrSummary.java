package com.padle.core.padelcoreservice.service.marketing;

import java.time.LocalDate;
import java.util.List;

public record QrSummary(LocalDate from, LocalDate to, List<QrStats> sources) {}
