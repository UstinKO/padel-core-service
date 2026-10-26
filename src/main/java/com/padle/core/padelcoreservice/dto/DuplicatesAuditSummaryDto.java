package com.padle.core.padelcoreservice.dto;

import java.util.Map;

// Сводные цифры отчёта об аудите дублей игроков.
public record DuplicatesAuditSummaryDto(
        long totalRecords,
        long duplicateGroupsCount,
        long uniquePlayersEstimate,
        Map<String, Long> recordsPerPlayerDistribution
) {
}
