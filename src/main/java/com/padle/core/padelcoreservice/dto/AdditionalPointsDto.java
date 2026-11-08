package com.padle.core.padelcoreservice.dto;

import java.util.List;

public record AdditionalPointsDto(long total, List<Stage> stages) {
    public static final AdditionalPointsDto EMPTY = new AdditionalPointsDto(0, List.of());

    public record Stage(int roundNumber, long points) {}
}
