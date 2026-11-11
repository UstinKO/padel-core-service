package com.padle.core.padelcoreservice.dto;

import java.util.List;

public record PublicCatalogPage(List<PublicTournamentDto> items, int page, int size,
        long totalElements, int totalPages) {
    public PublicCatalogPage { items = List.copyOf(items); }
}
