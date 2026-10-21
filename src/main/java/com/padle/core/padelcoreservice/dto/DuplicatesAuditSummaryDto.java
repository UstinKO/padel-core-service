package com.padle.core.padelcoreservice.dto;

import java.util.Map;

// LFPT-0677: сводные цифры отчёта об аудите дублей игроков.
public record DuplicatesAuditSummaryDto(
        long totalRecords,
        long duplicateGroupsCount,
        long uniquePlayersEstimate,
        Map<String, Long> recordsPerPlayerDistribution
) {
}
