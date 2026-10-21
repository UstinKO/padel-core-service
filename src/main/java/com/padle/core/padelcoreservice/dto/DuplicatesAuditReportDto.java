package com.padle.core.padelcoreservice.dto;

import java.util.List;
import java.util.Map;

// LFPT-0677: полный отчёт GET /api/admin/players/duplicates-audit.
public record DuplicatesAuditReportDto(
        DuplicatesAuditSummaryDto summary,
        List<DuplicateGroupDto> exactDuplicates,
        List<DuplicateGroupDto> probableDuplicates,
        List<PlayerSummaryDto> partnerFlowSuspects,
        Map<String, Long> sourceBreakdown
) {
}
