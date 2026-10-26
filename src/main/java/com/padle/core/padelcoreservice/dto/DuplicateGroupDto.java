package com.padle.core.padelcoreservice.dto;

import java.util.List;

// Группа записей PlayerPadel, совпавших по matchType на нормализованном matchKey.
public record DuplicateGroupDto(
        DuplicateMatchType matchType,
        String matchKey,
        List<PlayerSummaryDto> members
) {
}
